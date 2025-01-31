package cn.iocoder.yudao.module.iot.service.device.control;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.iot.api.device.dto.control.downstream.IotDeviceDownstreamAbstractReqDTO;
import cn.iocoder.yudao.module.iot.api.device.dto.control.downstream.IotDevicePropertyGetReqDTO;
import cn.iocoder.yudao.module.iot.api.device.dto.control.downstream.IotDevicePropertySetReqDTO;
import cn.iocoder.yudao.module.iot.api.device.dto.control.downstream.IotDeviceServiceInvokeReqDTO;
import cn.iocoder.yudao.module.iot.controller.admin.device.vo.control.IotDeviceDownstreamReqVO;
import cn.iocoder.yudao.module.iot.dal.dataobject.device.IotDeviceDO;
import cn.iocoder.yudao.module.iot.dal.dataobject.plugin.IotPluginInstanceDO;
import cn.iocoder.yudao.module.iot.enums.device.IotDeviceMessageIdentifierEnum;
import cn.iocoder.yudao.module.iot.enums.device.IotDeviceMessageTypeEnum;
import cn.iocoder.yudao.module.iot.mq.message.IotDeviceMessage;
import cn.iocoder.yudao.module.iot.mq.producer.device.IotDeviceProducer;
import cn.iocoder.yudao.module.iot.service.device.IotDeviceService;
import cn.iocoder.yudao.module.iot.service.plugin.IotPluginInstanceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.iot.enums.ErrorCodeConstants.DEVICE_DOWNSTREAM_FAILED;

/**
 * IoT 设备下行 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class IotDeviceDownstreamServiceImpl implements IotDeviceDownstreamService {

    @Resource
    private IotDeviceService deviceService;
    @Resource
    private IotPluginInstanceService pluginInstanceService;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private IotDeviceProducer deviceProducer;

    @Override
    public void downstreamDevice(IotDeviceDownstreamReqVO downstreamReqVO) {
        // 校验设备是否存在
        IotDeviceDO device = deviceService.validateDeviceExists(downstreamReqVO.getId());
        // TODO 芋艿：父设备的处理
        IotDeviceDO parentDevice = null;

        // 服务调用
        if (Objects.equals(downstreamReqVO.getType(), IotDeviceMessageTypeEnum.SERVICE.getType())) {
            invokeDeviceService(downstreamReqVO, device, parentDevice);
            return;
        }
        // 属性相关
        if (Objects.equals(downstreamReqVO.getType(), IotDeviceMessageTypeEnum.PROPERTY.getType()))
            // 属性设置
            if (Objects.equals(downstreamReqVO.getIdentifier(),
                    IotDeviceMessageIdentifierEnum.PROPERTY_SET.getIdentifier())) {
                setDeviceProperty(downstreamReqVO, device, parentDevice);
                return;
            }
        // 属性设置
        if (Objects.equals(downstreamReqVO.getIdentifier(),
                IotDeviceMessageIdentifierEnum.PROPERTY_GET.getIdentifier())) {
            getDeviceProperty(downstreamReqVO, device, parentDevice);
            return;
        }
        // TODO 芋艿：ota 升级
        // TODO 芋艿：配置下发
    }

    /**
     * 调用设备服务
     *
     * @param downstreamReqVO 下行请求
     * @param device          设备
     * @param parentDevice    父设备
     */
    @SuppressWarnings("unchecked")
    private void invokeDeviceService(IotDeviceDownstreamReqVO downstreamReqVO,
                                     IotDeviceDO device, IotDeviceDO parentDevice) {
        // 1. 参数校验
        if (!(downstreamReqVO.getData() instanceof Map<?, ?>)) {
            throw new ServiceException(BAD_REQUEST.getCode(), "data 不是 Map 类型");
        }
        // TODO @super：【可优化】过滤掉不合法的服务

        // 2. 发送请求
        String url = String.format( "sys/%s/%s/thing/service/%s",
                getProductKey(device, parentDevice), getDeviceName(device, parentDevice), downstreamReqVO.getIdentifier());
         IotDeviceServiceInvokeReqDTO reqDTO = new IotDeviceServiceInvokeReqDTO()
                .setParams((Map<String, Object>) downstreamReqVO.getData());
        CommonResult<Boolean> result = requestPlugin(url, reqDTO, device);

        // 3. 发送设备消息
        IotDeviceMessage message = new IotDeviceMessage().setRequestId(reqDTO.getRequestId())
                .setType(IotDeviceMessageTypeEnum.SERVICE.getType()).setIdentifier(reqDTO.getIdentifier())
                .setData(reqDTO.getParams());
        sendDeviceMessage(message, device, result.getCode());

        // 4. 如果不成功，抛出异常，提示用户
        if (result.isError()) {
            log.error("[invokeDeviceService][设备({})服务调用失败，请求参数：({})，响应结果：({})]",
                    device.getDeviceKey(), reqDTO, result);
            throw exception(DEVICE_DOWNSTREAM_FAILED, result.getMsg());
        }
    }

    /**
     * 设置设备属性
     *
     * @param downstreamReqVO 下行请求
     * @param device          设备
     * @param parentDevice    父设备
     */
    @SuppressWarnings("unchecked")
    private void setDeviceProperty(IotDeviceDownstreamReqVO downstreamReqVO,
                                   IotDeviceDO device, IotDeviceDO parentDevice) {
        // 1. 参数校验
        if (!(downstreamReqVO.getData() instanceof Map<?, ?>)) {
            throw new ServiceException(BAD_REQUEST.getCode(), "data 不是 Map 类型");
        }
        // TODO @super：【可优化】过滤掉不合法的属性

        // 2. 发送请求
        String url = String.format( "sys/%s/%s/thing/service/property/set",
                getProductKey(device, parentDevice), getDeviceName(device, parentDevice));
        IotDevicePropertySetReqDTO reqDTO = new IotDevicePropertySetReqDTO()
                .setProperties((Map<String, Object>) downstreamReqVO.getData());
        CommonResult<Boolean> result = requestPlugin(url, reqDTO, device);

        // 3. 发送设备消息
        IotDeviceMessage message = new IotDeviceMessage().setRequestId(reqDTO.getRequestId())
                .setType(IotDeviceMessageTypeEnum.PROPERTY.getType())
                .setIdentifier(IotDeviceMessageIdentifierEnum.PROPERTY_SET.getIdentifier())
                .setData(reqDTO.getProperties());
        sendDeviceMessage(message, device, result.getCode());

        // 4. 如果不成功，抛出异常，提示用户
        if (result.isError()) {
            log.error("[setDeviceProperty][设备({})属性设置失败，请求参数：({})，响应结果：({})]",
                    device.getDeviceKey(), reqDTO, result);
            throw exception(DEVICE_DOWNSTREAM_FAILED, result.getMsg());
        }
    }

    /**
     * 获取设备属性
     *
     * @param downstreamReqVO 下行请求
     * @param device          设备
     * @param parentDevice    父设备
     */
    @SuppressWarnings("unchecked")
    private void getDeviceProperty(IotDeviceDownstreamReqVO downstreamReqVO,
                                   IotDeviceDO device, IotDeviceDO parentDevice) {
        // 1. 参数校验
        if (!(downstreamReqVO.getData() instanceof List<?>)) {
            throw new ServiceException(BAD_REQUEST.getCode(), "data 不是 List 类型");
        }
        // TODO @super：【可优化】过滤掉不合法的属性

        // 2. 发送请求
        String url = String.format( "sys/%s/%s/thing/service/property/get",
                getProductKey(device, parentDevice), getDeviceName(device, parentDevice));
        IotDevicePropertyGetReqDTO reqDTO = new IotDevicePropertyGetReqDTO()
                .setIdentifiers((List<String>) downstreamReqVO.getData());
        CommonResult<Boolean> result = requestPlugin(url, reqDTO, device);

        // 3. 发送设备消息
        IotDeviceMessage message = new IotDeviceMessage().setRequestId(reqDTO.getRequestId())
                .setType(IotDeviceMessageTypeEnum.PROPERTY.getType())
                .setIdentifier(IotDeviceMessageIdentifierEnum.PROPERTY_SET.getIdentifier())
                .setData(reqDTO.getIdentifiers());
        sendDeviceMessage(message, device, result.getCode());

        // 4. 如果不成功，抛出异常，提示用户
        if (result.isError()) {
            log.error("[getDeviceProperty][设备({})属性获取失败，请求参数：({})，响应结果：({})]",
                    device.getDeviceKey(), reqDTO, result);
            throw exception(DEVICE_DOWNSTREAM_FAILED, result.getMsg());
        }
    }

    /**
     * 请求插件
     *
     * @param url     URL
     * @param reqDTO  请求参数，只需要设置子类的参数！
     * @param device  设备
     * @return 响应结果
     */
    @SuppressWarnings({"unchecked", "HttpUrlsUsage"})
    private CommonResult<Boolean> requestPlugin(String url, IotDeviceDownstreamAbstractReqDTO reqDTO, IotDeviceDO device) {
        // 获得设备对应的插件实例
        IotPluginInstanceDO pluginInstance = pluginInstanceService.getPluginInstanceByDeviceKey(device.getDeviceKey());
        if (pluginInstance == null) {
            throw exception(DEVICE_DOWNSTREAM_FAILED, "设备找不到对应的插件实例");
        }

        // 补充通用参数
        reqDTO.setRequestId(IdUtil.fastSimpleUUID());

        // 执行请求
        ResponseEntity<CommonResult<Boolean>> responseEntity;
        try {
            responseEntity = restTemplate.postForEntity(
                    String.format("http://%s:%d/%s", pluginInstance.getHostIp(), pluginInstance.getDownstreamPort(), url),
                    reqDTO, (Class<CommonResult<Boolean>>) (Class<?>) CommonResult.class);
            Assert.isTrue(responseEntity.getStatusCode().is2xxSuccessful(),
                    "HTTP 状态码不是 2xx，而是" + responseEntity.getStatusCode());
            Assert.notNull(responseEntity.getBody(), "响应结果不能为空");
        } catch (Exception ex) {
            log.error("[requestPlugin][设备({}) url({}) 下行消息失败，请求参数({})]", device.getDeviceKey(), url, reqDTO, ex);
            throw exception(DEVICE_DOWNSTREAM_FAILED, ExceptionUtil.getMessage(ex));
        }
        return responseEntity.getBody();
    }

    private void sendDeviceMessage(IotDeviceMessage message, IotDeviceDO device, Integer code) {
        // 1. 完善消息
        message.setProductKey(device.getProductKey()).setDeviceName(device.getDeviceName())
                .setDeviceKey(device.getDeviceKey());
        Assert.notNull(message.getRequestId(), "requestId 不能为空");
        if (message.getReportTime() == null) {
            message.setReportTime(LocalDateTime.now());
        }
        message.setCode(code);

        // 2. 发送消息
        try {
            deviceProducer.sendDeviceMessage(message);
            log.info("[sendDeviceMessage][message({}) 发送消息成功]", message);
        } catch (Exception e) {
            log.error("[sendDeviceMessage][message({}) 发送消息失败]", message, e);
        }
    }

    private String getDeviceName(IotDeviceDO device, IotDeviceDO parentDevice) {
        return parentDevice != null ? parentDevice.getDeviceName() : device.getDeviceName();
    }

    private String getProductKey(IotDeviceDO device, IotDeviceDO parentDevice) {
        return parentDevice != null ? parentDevice.getProductKey() : device.getProductKey();
    }

}

package cn.iocoder.yudao.module.iot.dal.dataobject.device;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.mybatis.core.type.LongSetTypeHandler;
import cn.iocoder.yudao.module.iot.dal.dataobject.product.IotProductDO;
import cn.iocoder.yudao.module.iot.enums.device.IotDeviceStateEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * IoT 设备 DO
 *
 * @author haohao
 */
@TableName(value = "iot_device", autoResultMap = true)
@KeySequence("iot_device_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IotDeviceDO extends BaseDO {

    /**
     * 设备 ID，主键，自增
     */
    @TableId
    private Long id;
    /**
     * 设备唯一标识符，全局唯一，用于识别设备
     *
     * 类似阿里云 <a href="https://help.aliyun.com/zh/iot/developer-reference/api-querydeviceinfo">QueryDeviceInfo</a> 的 IotInstanceId
     */
    private String deviceKey;
    /**
     * 设备名称，在产品内唯一，用于标识设备
     */
    private String deviceName;
    /**
     * 设备备注名称
     */
    private String nickname;
    /**
     * 设备序列号
     */
    private String serialNumber;
    /**
     * 设备图片
     */
    private String picUrl;
    /**
     * 设备分组编号集合
     *
     * 关联 {@link IotDeviceGroupDO#getId()}
     */
    @TableField(typeHandler = LongSetTypeHandler.class)
    private Set<Long> groupIds;

    /**
     * 产品编号
     * <p>
     * 关联 {@link IotProductDO#getId()}
     */
    private Long productId;
    /**
     * 产品标识
     * <p>
     * 冗余 {@link IotProductDO#getProductKey()}
     */
    private String productKey;
    /**
     * 设备类型
     * <p>
     * 冗余 {@link IotProductDO#getDeviceType()}
     */
    private Integer deviceType;
    /**
     * 网关设备编号
     * <p>
     * 子设备需要关联的网关设备 ID
     * <p>
     * 关联 {@link IotDeviceDO#getId()}
     */
    private Long gatewayId;

    /**
     * 设备状态
     * <p>
     * 枚举 {@link IotDeviceStateEnum}
     */
    private Integer state;
    /**
     * 最后上线时间
     */
    private LocalDateTime onlineTime;
    /**
     * 最后离线时间
     */
    private LocalDateTime offlineTime;
    /**
     * 设备激活时间
     */
    private LocalDateTime activeTime;

    /**
     * 设备的 IP 地址
     */
    private String ip;
    /**
     * 设备的固件版本
     */
    private String firmwareVersion;

    /**
     * 设备密钥，用于设备认证，需安全存储
     */
    private String deviceSecret;
    /**
     * MQTT 客户端 ID
     */
    private String mqttClientId;
    /**
     * MQTT 用户名
     */
    private String mqttUsername;
    /**
     * MQTT 密码
     */
    private String mqttPassword;
    /**
     * 认证类型（如一机一密、动态注册）
     */
    // TODO @haohao：是不是要枚举哈
    private String authType;

    /**
     * 设备位置的纬度
     */
    private BigDecimal latitude;
    /**
     * 设备位置的经度
     */
    private BigDecimal longitude;
    /**
     * 地区编码
     * <p>
     * 关联 Area 的 id
     */
    private Integer areaId;
    /**
     * 设备详细地址
     */
    private String address;

    /**
     * 设备配置
     *
     * JSON 格式，可下发给 device 进行自定义配置
     */
    private String config;

}
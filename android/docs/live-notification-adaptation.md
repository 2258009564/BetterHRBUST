# 原生实时通知适配与验收状态

核验日期：2026-10-12。普通课表通知可作为兼容入口，但不能等同于已接入所有厂商的原生岛。不能以改包名、伪装媒体/通话、忽略权限或虚构厂商字段替代实际适配。

| 平台 | 当前实现或可核验接口 | 完成验证所缺条件 |
| --- | --- | --- |
| Android 16 / 36.1 | 已构建标准通知与提升请求；按完整SDK版本兼容资格规则；16首版模拟器实测发送、撤销与资格特征 | 36.1及各厂商系统的实际提升效果待对应设备核验 |
| 小米焦点通知 / 超级岛 | 已实现协议、权限与岛功能查询；param_v2及大小岛模板；构建支持xiaomiAppId | 维护者AppId、实际包名/签名登记、场景授权、调试设备授权及HyperOS真机 |
| vivo 原子岛 | 官方关联接入指导可定位；客户端本地通知使用notification.superx.*，区分创建/更新/结束；尚未添加适配代码 | vivo官方场景授权、完整本地字段与能力查询核验、对应设备 |
| 荣耀灵动胶囊 | 官方支持页只保证部分预置功能和YOYO服务；公开设计指南不是可调用SDK | 确认课表场景的YOYO接入渠道、权限与对应设备；不能拿通用PUSH接口当胶囊接口 |
| OPPO 流体云 | 官方开发文档入口已定位，当前读取未获得完整技术正文 | 获取可核验的官方客户端接口、接入资格和设备；不猜测字段或加入未知SDK |
| 三星 Now Bar | 当前仅使用Android通用通知入口，不宣称专有Now Bar适配 | 机型/One UI版本、厂商资格和实际显示结果 |

## 小米联调前提

1. 按官方接入流程创建应用，申请课表场景并登记签名。正式包与.debug.widgetpreview测试包必须按实际身份登记，不能用正式包授权推断测试包也获授权。
2. 用 `-PxiaomiAppId=平台AppId` 构建对应身份；构建类型自动配置Debug标识。提供AppId并不等同于权限获批。
3. 添加调试设备，确认系统notification_focus_protocol、persist.sys.feature.island及canShowFocus查询结果。
4. 真机覆盖临近上课、上课、下课、停止跟踪、通知权限撤销、切换账号、系统时间/时区改变与后台回收。记录APK哈希、包名、证书、系统完整版本及实际显示。

## 来源

- 小米开发指南：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
- 小米接入流程：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132
- Android实时通知：https://developer.android.com/develop/ui/views/notifications/live-update
- vivo原子通知接入指导：https://dev.vivo.com.cn/documentCenter/doc/894
- vivo核心参数：https://dev.vivo.com.cn/documentCenter/doc/896
- 阿里云vivo通道官方接入指南（用于定位vivo官方链接，不能替代vivo场景审核）：https://help.aliyun.com/zh/document_detail/3030718.html
- 荣耀灵动胶囊支持范围：https://www.honor.com/cn/support/content/zh-cn15886369/
- 荣耀全局触达设计指南：https://developer.honor.com/cn/doc/guides/101538
- OPPO流体云文档入口：https://open.oppomobile.com/new/developmentDoc/info?id=13330

尚缺资料时保持原生岛验收未完成，不把兼容通知、单元测试或JSON结构正确当成厂商真机验收。

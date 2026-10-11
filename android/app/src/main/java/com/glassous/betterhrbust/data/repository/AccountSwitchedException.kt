package com.glassous.betterhrbust.data.repository

/**
 * 业务信号：请求发起时的账号已切换/登出，在途结果必须丢弃。
 *
 * 必须是普通 Exception 而非 kotlinx.coroutines.CancellationException——
 * 后者会被协程机制当作真取消处理：穿透 catch(Exception)、取消兄弟协程、
 * 并让页面刷新协程以「已取消」结束（跳过其后的状态复位代码）。
 * 页面层捕获本异常后静默结束即可（旧页面随账号切换自然废弃）。
 */
class AccountSwitchedException(message: String = "账号已切换，旧请求已丢弃") : Exception(message)

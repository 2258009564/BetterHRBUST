package com.glassous.betterhrbust.core.network

import java.io.IOException

class SessionExpiredException(message: String = "会话已过期，请重新登录") : IOException(message)

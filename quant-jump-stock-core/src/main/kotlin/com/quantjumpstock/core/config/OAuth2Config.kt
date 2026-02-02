package com.quantjumpstock.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "oauth2")
class OAuth2Config {
    var google: GoogleOAuthConfig = GoogleOAuthConfig()
    var naver: NaverOAuthConfig = NaverOAuthConfig()
    var frontendRedirectUrl: String = "http://localhost:3000/auth"
}

class GoogleOAuthConfig {
    var clientId: String = ""
    var clientSecret: String = ""
    var redirectUri: String = ""
    var authUrl: String = "https://accounts.google.com/o/oauth2/v2/auth"
    var tokenUrl: String = "https://oauth2.googleapis.com/token"
    var userInfoUrl: String = "https://www.googleapis.com/oauth2/v2/userinfo"
    var scope: String = "openid,email,profile"
}

class NaverOAuthConfig {
    var clientId: String = ""
    var clientSecret: String = ""
    var redirectUri: String = ""
    var authUrl: String = "https://nid.naver.com/oauth2.0/authorize"
    var tokenUrl: String = "https://nid.naver.com/oauth2.0/token"
    var userInfoUrl: String = "https://openapi.naver.com/v1/nid/me"
}

package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.domain.TokenPair;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class CookieService {
 private final AuthProperties properties; public CookieService(AuthProperties properties){this.properties=properties;}
 public void writeTokenCookies(HttpServletResponse response, TokenPair pair){add(response,properties.cookie().accessName(),pair.accessToken(),properties.accessTokenTtl().toSeconds(),"/");add(response,properties.cookie().refreshName(),pair.refreshToken(),properties.refreshTokenTtl().toSeconds(),"/api/v1/auth");}
 public void clearTokenCookies(HttpServletResponse response){add(response,properties.cookie().accessName(),"",0,"/");add(response,properties.cookie().refreshName(),"",0,"/api/v1/auth");}
 private void add(HttpServletResponse response,String name,String value,long maxAge,String path){ResponseCookie c=ResponseCookie.from(name,value).httpOnly(true).secure(properties.cookie().secure()).sameSite(properties.cookie().sameSite()).path(path).maxAge(maxAge).build();response.addHeader("Set-Cookie",c.toString());}
}

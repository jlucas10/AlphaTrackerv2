package com.alphatracker.api.demo;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Put on a controller method the shared demo account must not reach (file uploads today; change password,
// change email and delete account if those endpoints are ever added). DemoGuardInterceptor answers 403
// before the method runs. Normal users are unaffected.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BlockedForDemo {
}

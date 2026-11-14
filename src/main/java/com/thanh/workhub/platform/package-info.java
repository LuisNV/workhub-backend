/**
 * Hạ tầng dùng chung (web, security, storage). Đánh dấu OPEN để mọi module nghiệp vụ được dùng
 * các package con. Quy tắc: platform KHÔNG được import bất kỳ module nghiệp vụ nào (user, auth, ...).
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.thanh.workhub.platform;

import org.springframework.modulith.ApplicationModule;

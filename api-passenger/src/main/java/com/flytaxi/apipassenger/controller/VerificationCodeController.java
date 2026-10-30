package com.flytaxi.apipassenger.controller;

import com.flytaxi.internalCommon.dto.ResponseResult;
import com.flytaxi.internalCommon.request.VerificationCodeDTO;
import com.flytaxi.apipassenger.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Date: 2024-10-12-19:08
 * @Description: com.flytaxi.apipassenger.controller
 */
@RestController
@Slf4j
public class VerificationCodeController {

    @Autowired
    private VerificationCodeService service;



    @GetMapping("/verification-code")
    public ResponseResult verificationCode(@RequestParam(required = false) String passengerPhone,
                                           @RequestBody(required = false) VerificationCodeDTO codeDTO) {
        String phone = passengerPhone;
        if (phone == null && codeDTO != null) {
            phone = codeDTO.getPassengerPhone();
        }
        log.debug("Passenger verification code requested");
        return service.generateCode(phone);
    }

    @PostMapping("/verification-code_check")
    public ResponseResult checkVerificationCode(@RequestBody VerificationCodeDTO codeDTO) {
        String passengerPhone = codeDTO.getPassengerPhone();
        String verificationCode = codeDTO.getVerificationCode();
        log.debug("Checking passenger verification code");
        return service.checkCode(passengerPhone, verificationCode);
    }
}

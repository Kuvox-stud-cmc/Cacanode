package com.cacanode.api.tenant.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cacanode.api.common.controller.BaseController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Tenants", description = "Endpoints for tenant management")
@RestController
@RequestMapping({"/api/v1/tenants", "/api/tenants"})
@RequiredArgsConstructor
public class TenantController extends BaseController {

  

}

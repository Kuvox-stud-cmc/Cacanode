package com.cacanode.api.recruitment.api;

import java.util.UUID;

public interface RecruitmentEmailDeliveryCallbackApi {
    void complete(UUID deliveryId,boolean success,String error);
}

package com.rink.ice.dto;

/**
 * 发鞋 / 归还请求体。
 * 发鞋：memberId + courseId + shoeSize，冰刀由柜员按尺码从库里实际占一双，前端不指定具体冰刀。
 * 归还：rentalId 指定收口哪张单；damaged=true 表示刀刃损坏，note 登记问题。
 */
public class SkateRequest {
    public Long memberId;
    public Long courseId;
    public Integer shoeSize;
    public Long rentalId;
    public boolean damaged;
    public String note;
}

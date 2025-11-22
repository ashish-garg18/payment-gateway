package com.paymentgateway.service;

public interface DowntimeService {
    boolean isInstrumentDown(String instrumentType, String issuer);
}

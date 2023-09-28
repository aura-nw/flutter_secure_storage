package com.it_nomads.fluttersecurestorage;

public interface IExceptionObserver {
    public void onUserUnAuthorizeOrError(Exception e);

    public void onUserAuthorize();
}

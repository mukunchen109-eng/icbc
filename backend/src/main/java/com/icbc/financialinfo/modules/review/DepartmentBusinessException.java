package com.icbc.financialinfo.modules.review;

public class DepartmentBusinessException extends RuntimeException {

  private final int status;

  public DepartmentBusinessException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}

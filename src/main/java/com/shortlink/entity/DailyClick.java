package com.shortlink.entity;

import java.time.LocalDate;
public class DailyClick {
    private LocalDate clickDate;
    private Long clickCount;

    public Long getClickCount() {return clickCount;}
    public void setClickCount(Long clickCount) {this.clickCount = clickCount;}

    public LocalDate getClickDate() {return clickDate;}
    public void setClickDate(LocalDate clickDate) {this.clickDate = clickDate;}
}

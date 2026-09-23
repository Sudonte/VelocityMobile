package com.example.velocitysuites.network.dto;

import java.util.List;

public class NotificationDto {
    public long id;
    public long user_id;
    public String title;
    public String message;
    public String category;
    public Long reference_id;
    public List<String> target_audience;
    public boolean is_read;
    public String created_at;
}

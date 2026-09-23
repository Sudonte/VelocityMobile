package com.example.velocitysuites.network.dto;

import java.util.List;

public class AnnouncementDto {
    public long id;
    public String title;
    public String content;
    public String published_at;
    public List<String> target_audience;
    public List<String> images;
}

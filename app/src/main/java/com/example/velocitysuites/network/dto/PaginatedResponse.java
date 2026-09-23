package com.example.velocitysuites.network.dto;

import java.util.ArrayList;
import java.util.List;

public class PaginatedResponse<T> {
    public int current_page;
    public List<T> data = new ArrayList<>();
    public int last_page;
    public int total;
}

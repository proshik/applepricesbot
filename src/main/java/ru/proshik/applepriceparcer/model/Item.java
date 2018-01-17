package ru.proshik.applepriceparcer.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public class Item implements Serializable {

    private String title;
    private BigDecimal price;

    public Item() {
    }

    public Item(String title, BigDecimal price) {
        this.title = title;
        this.price = price;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getPrice() {
        return price;
    }

}

package com.colourmate.paintbynumber.model;

public class CategoryModel {
    String catName;
    String catURL;

    public CategoryModel(String catName, String catURL) {
        this.catName = catName;
        this.catURL = catURL;
    }

    public String getCatName() {
        return this.catName;
    }

    public String getCatURL() {
        return this.catURL;
    }
}
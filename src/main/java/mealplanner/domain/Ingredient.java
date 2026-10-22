package mealplanner.domain;

import java.util.HashSet;
import java.util.Set;


public class Ingredient {
    private long id;
    private String name;
    private String category;
    private Set<String> allergenTags = new HashSet<>();
    private String fdcId;
    private double gramsPerPiece;
    private double densityGPerMl;

    public Ingredient(long id, String name, String category,
                      double gramsPerPiece, double densityGPerMl) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.gramsPerPiece = gramsPerPiece;
        this.densityGPerMl = densityGPerMl;
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public String getCategory() {
        return category;
    }
    public Set<String> getAllergenTags() {
        return allergenTags;
    }
    public String getFdcId() {
        return fdcId;
    }
    public void setFdcId(String fdcId) {
        this.fdcId = fdcId;
    }
    public double getGramsPerPiece() {
        return gramsPerPiece;
    }
    public double getDensityGPerMl() {
        return densityGPerMl;
    }

    @Override
    public String toString(){
        return name;
    }
}

package com.thedariusz.todoai.category;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Free-text lookup over the {@code category} reference table, used by the
 * category picker's search box.
 */
@Service
public class CategorySearchService {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Category> searchByName(String name) {
        String sql = "SELECT * FROM category WHERE name_pl LIKE '%" + name + "%'";
        return entityManager.createNativeQuery(sql, Category.class).getResultList();
    }
}

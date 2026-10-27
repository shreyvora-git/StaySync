package com.staysync.dao;

import com.staysync.db.DataSourceProvider;
import com.staysync.error.AppException;
import com.staysync.model.Category;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Category records.
 */
public class CategoryDao {

    private static final String SELECT_COLUMNS =
        "category_id, category_name, description, is_confidential, is_active";

    private final DataSource dataSource;

    public CategoryDao(DataSource dataSource) {
        this.dataSource = dataSource != null ? dataSource : DataSourceProvider.getDataSource();
    }

    public CategoryDao() {
        this(DataSourceProvider.getDataSource());
    }

    /**
     * Retrieves all active complaint categories for the submission screen.
     */
    public List<Category> findActive() {
        try (Connection conn = dataSource.getConnection()) {
            return findActive(conn);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<Category> findActive(Connection conn) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM categories WHERE is_active = TRUE ORDER BY category_name ASC";
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Retrieves all categories, including archived/inactive ones.
     */
    public List<Category> findAll() {
        try (Connection conn = dataSource.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public List<Category> findAll(Connection conn) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM categories ORDER BY category_id ASC";
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Finds a single category by ID.
     */
    public Optional<Category> findById(long categoryId) {
        try (Connection conn = dataSource.getConnection()) {
            return findById(conn, categoryId);
        } catch (SQLException e) {
            throw AppException.wrap(e);
        }
    }

    public Optional<Category> findById(Connection conn, long categoryId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM categories WHERE category_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Inserts a new category within a transaction.
     */
    public long insert(Connection conn, Category category) throws SQLException {
        String sql = """
            INSERT INTO categories (category_name, description, is_confidential, is_active)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.categoryName());
            ps.setString(2, category.description());
            ps.setBoolean(3, category.isConfidential());
            ps.setBoolean(4, category.isActive());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting category failed, no ID obtained.");
            }
        }
    }

    /**
     * Updates an existing category within a transaction.
     */
    public boolean update(Connection conn, Category category) throws SQLException {
        String sql = """
            UPDATE categories
            SET category_name = ?, description = ?, is_confidential = ?, is_active = ?
            WHERE category_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.categoryName());
            ps.setString(2, category.description());
            ps.setBoolean(3, category.isConfidential());
            ps.setBoolean(4, category.isActive());
            ps.setLong(5, category.categoryId());

            return ps.executeUpdate() > 0;
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        long categoryId = rs.getLong("category_id");
        String categoryName = rs.getString("category_name");
        String description = rs.getString("description");
        boolean isConfidential = rs.getBoolean("is_confidential");
        boolean isActive = rs.getBoolean("is_active");

        return new Category(categoryId, categoryName, description, isConfidential, isActive);
    }
}

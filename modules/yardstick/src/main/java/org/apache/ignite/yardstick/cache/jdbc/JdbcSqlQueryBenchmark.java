package org.apache.ignite.yardstick.cache.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.yardstickframework.BenchmarkConfiguration;

import static org.yardstickframework.BenchmarkUtils.println;

/**
 * JDBC benchmark that performs query operations
 */
public class JdbcSqlQueryBenchmark extends JdbcAbstractBenchmark {
    private static final String SELECT_QUERY =
        "select p.id, p.org_id, p.first_name, p.last_name, p.salary " +
            "from PERSON p " +
            "where salary >= ? and salary <= ?";

    /** {@inheritDoc} */
    @Override public void setUp(BenchmarkConfiguration cfg) throws Exception {
        super.setUp(cfg);

        println(cfg, "Populating query data...");

        long start = System.nanoTime();

        try (PreparedStatement stmt = conn.get().prepareStatement("insert into PERSON(id, first_name, last_name," +
            " salary) values(?, ?, ?, ?)")) {
            // Populate persons.
            for (int i = 0; i < args.range() && !Thread.currentThread().isInterrupted(); i++) {
                stmt.setInt(1, i);
                stmt.setString(2, "firstName" + i);
                stmt.setString(3, "lastName" + i);
                stmt.setDouble(4, i * 1000);
                stmt.addBatch();

                if (i % 100000 == 0)
                    println(cfg, "Populated persons: " + i);
            }
            stmt.executeBatch();
            conn.get().commit();
        }

        println(cfg, "Finished populating join query data in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");
    }

    /** {@inheritDoc} */
    @Override public boolean test(Map<Object, Object> ctx) throws Exception {
        double salary = ThreadLocalRandom.current().nextDouble() * args.range() * 1000;

        double maxSalary = salary + 1000;

        try (PreparedStatement stmt = conn.get().prepareStatement(SELECT_QUERY)) {
            stmt.setDouble(1, salary);
            stmt.setDouble(2, maxSalary);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double actualSalary = rs.getDouble(5);

                    if (actualSalary < salary || actualSalary > maxSalary)
                        throw new Exception("Invalid person retrieved [min=" + salary + ", max=" + maxSalary +
                            ", salary=" + actualSalary + ", id=" + rs.getInt(1) + ']');
                }
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public void tearDown() throws Exception {
        if (!args.createTempDatabase())
            clearTable("PERSON");
        super.tearDown();
    }
}

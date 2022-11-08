package org.kenyahmis.loadctpatients;

import org.apache.commons.io.IOUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.storage.StorageLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.apache.spark.sql.functions.lit;

public class LoadCTPatients {
    private static final Logger logger = LoggerFactory.getLogger(LoadCTPatients.class);

    public static void main(String[] args) {
        SparkConf conf = new SparkConf();
        conf.setAppName("Load CT Patients");
        SparkSession session = SparkSession.builder()
                .config(conf)
                .getOrCreate();
        RuntimeConfig rtConfig = session.conf();

        final String sourceQueryFileName = "LoadSourceCTPatients.sql";
        final String targetQueryFileName = "LoadTargetCTPatients.sql";
        String sourceVisitsQuery;
        String targetQuery;
        InputStream inputStream = LoadCTPatients.class.getClassLoader().getResourceAsStream(sourceQueryFileName);
        if (inputStream == null) {
            logger.error(sourceQueryFileName + " not found");
            return;
        }
        try {
            sourceVisitsQuery = IOUtils.toString(inputStream, Charset.defaultCharset());
        } catch (IOException e) {
            logger.error("Failed to load source CT patients query from file", e);
            return;
        }

        InputStream targetQueryInputStream = LoadCTPatients.class.getClassLoader().getResourceAsStream(targetQueryFileName);
        if (targetQueryInputStream == null) {
            logger.error(targetQueryFileName + " not found");
            return;
        }
        try {
            targetQuery = IOUtils.toString(targetQueryInputStream, Charset.defaultCharset());
        } catch (IOException e) {
            logger.error("Failed to load target CT patient from file", e);
            return;
        }
        logger.info("Loading source CT patients");
        Dataset<Row> sourceDf = session.read()
                .format("jdbc")
                .option("url", rtConfig.get("spark.source.url"))
                .option("driver", rtConfig.get("spark.source.driver"))
                .option("user", rtConfig.get("spark.source.user"))
                .option("password", rtConfig.get("spark.source.password"))
                .option("dbtable", "(" + sourceVisitsQuery + ") pv")
                .option("numpartitions", rtConfig.get("spark.source.numpartitions"))
                .load();
        sourceDf = sourceDf
                .withColumn("RegistrationYear", lit(null).cast(DataTypes.IntegerType))
                .withColumn("MPIPKV", lit(null));

        sourceDf.printSchema();

        sourceDf.persist(StorageLevel.MEMORY_AND_DISK());

        logger.info("Loading target CT patients");
        Dataset<Row> targetDf = session.read()
                .format("jdbc")
                .option("url", rtConfig.get("spark.sink.url"))
                .option("driver", rtConfig.get("spark.sink.driver"))
                .option("user", rtConfig.get("spark.sink.user"))
                .option("password", rtConfig.get("spark.sink.password"))
                .option("dbtable", "(" + targetQuery + ") ps")
                .load();

        targetDf.persist(StorageLevel.MEMORY_AND_DISK());
        targetDf.printSchema();

        // Find rows in target table unmatched in source table
        Dataset<Row> unmatchedDf = targetDf.except(sourceDf);

        // Will "update" all rows matched, insert new rows and maintain any unmatched rows
        Dataset<Row> finalMergeDf = sourceDf.unionAll(unmatchedDf);

        logger.info("Writing final dataframe to target table");
        // Write to target table
        finalMergeDf
                .repartition(10)
                .write()
                .format("jdbc")
                .option("url", rtConfig.get("spark.sink.url"))
                .option("driver", rtConfig.get("spark.sink.driver"))
                .option("user", rtConfig.get("spark.sink.user"))
                .option("password", rtConfig.get("spark.sink.password"))
                .option("dbtable", rtConfig.get("spark.sink.dbtable"))
                .option("truncate", "true")
                .mode(SaveMode.Overwrite)
                .save();
    }
}

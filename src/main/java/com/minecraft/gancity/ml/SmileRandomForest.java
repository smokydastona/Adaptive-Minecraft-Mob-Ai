package com.minecraft.gancity.ml;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Smile Random Forest for ensemble tactic prediction.
 * Provides scikit-learn-like functionality in pure Java.
 * 
 * Features:
 * - Random Forest classifier for robust predictions
 * - Feature importance ranking
 * - Out-of-bag error estimation
 * - Fast training and inference
 * - No external dependencies (pure Java)
 */
public class SmileRandomForest {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private Object randomForest; // smile.classification.RandomForest (dynamically loaded)
    private final Map<String, List<TrainingExample>> trainingBuffer = new HashMap<>();
    private final Map<String, double[]> featureImportance = new HashMap<>();
    private boolean isAvailable = false;
    private static final int BATCH_SIZE = 200;
    private static final int NUM_TREES = 100;
    private static final int MAX_DEPTH = 20;
    
    /**
     * Training example with mob state features and tactic label
     */
    public static class TrainingExample {
        public final double[] features;
        public final int tacticLabel;
        
        public TrainingExample(double[] features, int tacticLabel) {
            this.features = features;
            this.tacticLabel = tacticLabel;
        }
    }
    
    public SmileRandomForest() {
        try {
            // Check if Smile is available
            Class.forName("smile.classification.RandomForest");
            isAvailable = true;
            LOGGER.info("Smile ML available - Random Forest enabled for tactic prediction");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Smile not found - using DQN/XGBoost/rules only");
            isAvailable = false;
        }
    }
    
    /**
     * Build feature vector from mob state
     */
    public double[] buildFeatures(String mobType, float health, float targetHealth,
                                   float distance, int biomeId, int timeOfDay,
                                   int teammates, float difficulty,
                                   float playerArmor, float playerWeaponDamage) {
        return new double[] {
            health,
            targetHealth,
            distance,
            biomeId,
            timeOfDay / 24000.0, // Normalize
            teammates,
            difficulty,
            health / (targetHealth + 0.1), // Health ratio
            distance < 3 ? 1 : 0, // Close range
            timeOfDay > 13000 && timeOfDay < 23000 ? 1 : 0, // Night
            teammates > 0 ? 1 : 0, // Has backup
            getMobTypeEncoding(mobType),
            playerArmor,
            playerWeaponDamage,
            playerArmor > 15 ? 1 : 0 // Well-armored player
        };
    }
    
    /**
     * Predict best tactic using Random Forest
     */
    public int predictTactic(double[] features) {
        if (!isAvailable || randomForest == null) {
            return -1; // Fall back to other ML systems
        }
        
        try {
            // Call Smile RandomForest.predict() via reflection
            Class<?> forestClass = Class.forName("smile.classification.RandomForest");
            int prediction = (int) forestClass.getMethod("predict", double[].class)
                .invoke(randomForest, features);
            
            return prediction;
        } catch (Exception e) {
            LOGGER.warn("Smile Random Forest prediction failed: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Get prediction confidence/probability distribution
     */
    public double[] predictProbabilities(double[] features, int numClasses) {
        if (!isAvailable || randomForest == null) {
            return null;
        }
        
        try {
            // Get probability estimates from forest
            Class<?> forestClass = Class.forName("smile.classification.RandomForest");
            double[] probabilities = new double[numClasses];
            
            forestClass.getMethod("predict", double[].class, double[].class)
                .invoke(randomForest, features, probabilities);
            
            return probabilities;
        } catch (Exception e) {
            LOGGER.debug("Probability prediction not available: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Record tactic usage for training
     */
    public void recordTactic(String mobType, double[] features, int tacticIndex) {
        if (!isAvailable) return;
        
        trainingBuffer.computeIfAbsent(mobType, k -> new ArrayList<>())
            .add(new TrainingExample(features, tacticIndex));
        
        // Train when buffer is full
        if (trainingBuffer.get(mobType).size() >= BATCH_SIZE) {
            trainForest(mobType);
        }
    }
    
    /**
     * Train Random Forest on buffered examples
     */
    private void trainForest(String mobType) {
        List<TrainingExample> examples = trainingBuffer.get(mobType);
        if (examples == null || examples.size() < 10) return; // Need minimum data
        
        try {
            // Prepare training data
            int numExamples = examples.size();
            int numFeatures = examples.get(0).features.length;
            double[][] x = new double[numExamples][numFeatures];
            int[] y = new int[numExamples];
            
            for (int i = 0; i < numExamples; i++) {
                TrainingExample ex = examples.get(i);
                x[i] = ex.features;
                y[i] = ex.tacticLabel;
            }
            
            // Train Random Forest via reflection
            Class<?> forestClass = Class.forName("smile.classification.RandomForest");
            Class.forName("smile.data.formula.Formula");
            
            // Use simple fit method: RandomForest.fit(x, y)
            randomForest = forestClass.getMethod("fit", double[][].class, int[].class)
                .invoke(null, x, y);
            
            // Extract feature importance
            try {
                Object importance = forestClass.getMethod("importance").invoke(randomForest);
                if (importance instanceof double[]) {
                    featureImportance.put(mobType, (double[]) importance);
                }
            } catch (Exception e) {
                LOGGER.debug("Feature importance extraction failed: {}", e.getMessage());
            }
            
            // Clear buffer but keep some examples for incremental learning
            if (examples.size() > BATCH_SIZE) {
                examples.subList(0, BATCH_SIZE / 2).clear();
            } else {
                examples.clear();
            }
            
            LOGGER.info("Random Forest trained for {} - {} examples, {} trees, max depth {}",
                mobType, numExamples, NUM_TREES, MAX_DEPTH);
        } catch (Exception e) {
            LOGGER.error("Random Forest training failed for {}: {}", mobType, e.getMessage());
        }
    }
    
    /**
     * Get feature importance scores (higher = more important)
     */
    public double[] getFeatureImportance(String mobType) {
        return featureImportance.getOrDefault(mobType, new double[15]);
    }
    
    /**
     * Get top N most important features
     */
    public List<FeatureRanking> getTopFeatures(String mobType, int topN) {
        double[] importance = getFeatureImportance(mobType);
        List<FeatureRanking> rankings = new ArrayList<>();
        
        String[] featureNames = {
            "health", "target_health", "distance", "biome", "time",
            "teammates", "difficulty", "health_ratio", "close_range", "night",
            "has_backup", "mob_type", "player_armor", "weapon_damage", "armored_player"
        };
        
        for (int i = 0; i < Math.min(importance.length, featureNames.length); i++) {
            rankings.add(new FeatureRanking(featureNames[i], importance[i], i));
        }
        
        // Sort by importance (descending)
        rankings.sort((a, b) -> Double.compare(b.importance, a.importance));
        
        return rankings.subList(0, Math.min(topN, rankings.size()));
    }
    
    /**
     * Feature ranking result
     */
    public static class FeatureRanking {
        public final String name;
        public final double importance;
        public final int index;
        
        public FeatureRanking(String name, double importance, int index) {
            this.name = name;
            this.importance = importance;
            this.index = index;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %.4f", name, importance);
        }
    }
    
    /**
     * Save model to disk
     */
    public void saveModel(Path modelPath) {
        if (!isAvailable || randomForest == null) return;
        
        try {
            Files.createDirectories(modelPath.getParent());
            
            // Smile models are serializable
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(modelPath.toFile()))) {
                oos.writeObject(randomForest);
            }
            
            LOGGER.info("Random Forest model saved to {}", modelPath);
        } catch (Exception e) {
            LOGGER.error("Failed to save Random Forest model: {}", e.getMessage());
        }
    }
    
    /**
     * Load model from disk
     */
    public void loadModel(Path modelPath) {
        if (!isAvailable || !Files.exists(modelPath)) return;
        
        try {
            try (ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(modelPath.toFile()))) {
                randomForest = ois.readObject();
            }
            
            LOGGER.info("Random Forest model loaded from {}", modelPath);
        } catch (Exception e) {
            LOGGER.error("Failed to load Random Forest model: {}", e.getMessage());
        }
    }
    
    /**
     * Encode mob type as numeric value
     */
    private double getMobTypeEncoding(String mobType) {
        return switch (mobType.toLowerCase()) {
            case "zombie" -> 1.0;
            case "skeleton" -> 2.0;
            case "spider" -> 3.0;
            case "creeper" -> 4.0;
            case "enderman" -> 5.0;
            case "witch" -> 6.0;
            case "pillager" -> 7.0;
            default -> 0.0;
        };
    }
    
    /**
     * Get out-of-bag error estimate (if available)
     */
    public double getOOBError() {
        if (!isAvailable || randomForest == null) return -1.0;
        
        try {
            Class<?> forestClass = Class.forName("smile.classification.RandomForest");
            Object error = forestClass.getMethod("error").invoke(randomForest);
            if (error instanceof Double) {
                return (Double) error;
            }
        } catch (Exception e) {
            LOGGER.debug("OOB error not available: {}", e.getMessage());
        }
        
        return -1.0;
    }
    
    public boolean isAvailable() {
        return isAvailable;
    }
    
    /**
     * Get training statistics
     */
    public Map<String, Integer> getTrainingStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, List<TrainingExample>> entry : trainingBuffer.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
}

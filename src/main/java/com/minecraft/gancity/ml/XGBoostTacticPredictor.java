package com.minecraft.gancity.ml;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * XGBoost-based tactic prediction for lightweight, explainable AI decisions.
 * Complements DQN by providing fast inference on tabular mob state data.
 * 
 * Features:
 * - Gradient boosting for combat tactic selection
 * - Feature importance analysis (which stats matter most)
 * - Incremental learning from combat outcomes
 * - Much smaller model size than neural networks
 */
public class XGBoostTacticPredictor {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private Object booster; // ml.dmlc.xgboost4j.java.Booster (dynamically loaded)
    private final Map<String, List<TrainingExample>> trainingBuffer = new HashMap<>();
    private final Map<String, float[]> featureImportance = new HashMap<>();
    private boolean isAvailable = false;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_DEPTH = 6;
    private static final float LEARNING_RATE = 0.1f;
    
    /**
     * Combat training example with mob state features
     */
    public static class TrainingExample {
        public final float[] features; // [health, targetHealth, distance, biome, time, teammates, ...]
        public final int tacticIndex;  // Which tactic was used
        public final float reward;     // Success (1.0) or failure (0.0)
        
        public TrainingExample(float[] features, int tacticIndex, float reward) {
            this.features = features;
            this.tacticIndex = tacticIndex;
            this.reward = reward;
        }
    }
    
    public XGBoostTacticPredictor() {
        try {
            // Check if XGBoost is available
            Class.forName("ml.dmlc.xgboost4j.java.Booster");
            isAvailable = true;
            LOGGER.info("XGBoost available - gradient boosting enabled for tactic prediction");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("XGBoost not found - using DQN/rules only");
            isAvailable = false;
        }
    }
    
    /**
     * Build feature vector from mob state
     */
    public float[] buildFeatures(String mobType, float health, float targetHealth, 
                                  float distance, int biomeId, int timeOfDay, 
                                  int teammates, float difficulty) {
        return new float[] {
            health,
            targetHealth,
            distance,
            biomeId,
            timeOfDay / 24000f, // Normalize to 0-1
            teammates,
            difficulty,
            health / (targetHealth + 0.1f), // Health ratio
            distance < 3 ? 1 : 0, // Close range flag
            timeOfDay > 13000 && timeOfDay < 23000 ? 1 : 0, // Night flag
            teammates > 0 ? 1 : 0, // Has backup flag
            getMobTypeEncoding(mobType) // Mob type as numeric
        };
    }
    
    /**
     * Predict best tactic index for given state
     */
    public int predictTactic(float[] features, int numTactics) {
        if (!isAvailable || booster == null) {
            return -1; // Fall back to DQN or rules
        }
        
        try {
            // Call XGBoost predict via reflection
            Class<?> dmatrixClass = Class.forName("ml.dmlc.xgboost4j.java.DMatrix");
            Object dmatrix = dmatrixClass.getConstructor(float[].class, int.class, int.class)
                .newInstance(features, 1, features.length);
            
            Class<?> boosterClass = Class.forName("ml.dmlc.xgboost4j.java.Booster");
            float[][] predictions = (float[][]) boosterClass.getMethod("predict", dmatrixClass)
                .invoke(booster, dmatrix);
            
            // Find tactic with highest predicted reward
            int bestTactic = 0;
            float bestScore = predictions[0][0];
            for (int i = 1; i < Math.min(predictions[0].length, numTactics); i++) {
                if (predictions[0][i] > bestScore) {
                    bestScore = predictions[0][i];
                    bestTactic = i;
                }
            }
            
            return bestTactic;
        } catch (Exception e) {
            LOGGER.warn("XGBoost prediction failed: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Record combat outcome for training
     */
    public void recordOutcome(String mobType, float[] features, int tacticIndex, boolean success) {
        if (!isAvailable) return;
        
        trainingBuffer.computeIfAbsent(mobType, k -> new ArrayList<>())
            .add(new TrainingExample(features, tacticIndex, success ? 1.0f : 0.0f));
        
        // Train when buffer is full
        if (trainingBuffer.get(mobType).size() >= BATCH_SIZE) {
            trainIncremental(mobType);
        }
    }
    
    /**
     * Incremental training on buffered examples
     */
    private void trainIncremental(String mobType) {
        List<TrainingExample> examples = trainingBuffer.get(mobType);
        if (examples == null || examples.isEmpty()) return;
        
        try {
            // Prepare training data
            int numExamples = examples.size();
            int numFeatures = examples.get(0).features.length;
            float[] flatFeatures = new float[numExamples * numFeatures];
            float[] labels = new float[numExamples];
            
            for (int i = 0; i < numExamples; i++) {
                TrainingExample ex = examples.get(i);
                System.arraycopy(ex.features, 0, flatFeatures, i * numFeatures, numFeatures);
                labels[i] = ex.reward;
            }
            
            // Create DMatrix and train via reflection
            Class<?> dmatrixClass = Class.forName("ml.dmlc.xgboost4j.java.DMatrix");
            Object trainData = dmatrixClass.getConstructor(float[].class, int.class, int.class)
                .newInstance(flatFeatures, numExamples, numFeatures);
            
            dmatrixClass.getMethod("setLabel", float[].class).invoke(trainData, labels);
            
            // Training parameters
            Map<String, Object> params = new HashMap<>();
            params.put("max_depth", MAX_DEPTH);
            params.put("eta", LEARNING_RATE);
            params.put("objective", "reg:squarederror");
            params.put("eval_metric", "rmse");
            
            // Train booster
            Class<?> boosterClass = Class.forName("ml.dmlc.xgboost4j.java.Booster");
            Class<?> xgboostClass = Class.forName("ml.dmlc.xgboost4j.java.XGBoost");
            
            if (booster == null) {
                // Initial training
                Map<String, Object> watches = new HashMap<>();
                booster = xgboostClass.getMethod("train", dmatrixClass, Map.class, int.class, 
                    Map.class, Object.class, Object.class)
                    .invoke(null, trainData, params, 10, watches, null, null);
            } else {
                // Update existing model
                boosterClass.getMethod("update", dmatrixClass, int.class)
                    .invoke(booster, trainData, 1);
            }
            
            // Extract feature importance
            String[] importanceMap = (String[]) boosterClass.getMethod("getScore", String.class, String.class)
                .invoke(booster, "", "gain");
            updateFeatureImportance(mobType, importanceMap);
            
            // Clear training buffer
            examples.clear();
            
            LOGGER.info("XGBoost trained for {} - {} examples processed", mobType, numExamples);
        } catch (Exception e) {
            LOGGER.error("XGBoost training failed for {}: {}", mobType, e.getMessage());
        }
    }
    
    /**
     * Update feature importance tracking
     */
    private void updateFeatureImportance(String mobType, String[] importanceMap) {
        float[] importance = new float[12]; // Number of features
        if (importanceMap != null) {
            for (String entry : importanceMap) {
                String[] parts = entry.split("=");
                if (parts.length == 2) {
                    try {
                        int featureIdx = Integer.parseInt(parts[0].substring(1)); // f0 -> 0
                        float gain = Float.parseFloat(parts[1]);
                        if (featureIdx < importance.length) {
                            importance[featureIdx] = gain;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        featureImportance.put(mobType, importance);
    }
    
    /**
     * Get feature importance for explainability
     */
    public float[] getFeatureImportance(String mobType) {
        return featureImportance.getOrDefault(mobType, new float[12]);
    }
    
    /**
     * Save model to disk
     */
    public void saveModel(Path modelPath) {
        if (!isAvailable || booster == null) return;
        
        try {
            Files.createDirectories(modelPath.getParent());
            Class<?> boosterClass = Class.forName("ml.dmlc.xgboost4j.java.Booster");
            boosterClass.getMethod("saveModel", String.class)
                .invoke(booster, modelPath.toString());
            LOGGER.info("XGBoost model saved to {}", modelPath);
        } catch (Exception e) {
            LOGGER.error("Failed to save XGBoost model: {}", e.getMessage());
        }
    }
    
    /**
     * Load model from disk
     */
    public void loadModel(Path modelPath) {
        if (!isAvailable || !Files.exists(modelPath)) return;
        
        try {
            Class<?> xgboostClass = Class.forName("ml.dmlc.xgboost4j.java.XGBoost");
            booster = xgboostClass.getMethod("loadModel", String.class)
                .invoke(null, modelPath.toString());
            LOGGER.info("XGBoost model loaded from {}", modelPath);
        } catch (Exception e) {
            LOGGER.error("Failed to load XGBoost model: {}", e.getMessage());
        }
    }
    
    /**
     * Encode mob type as numeric value
     */
    private float getMobTypeEncoding(String mobType) {
        return switch (mobType.toLowerCase()) {
            case "zombie" -> 1.0f;
            case "skeleton" -> 2.0f;
            case "spider" -> 3.0f;
            case "creeper" -> 4.0f;
            case "enderman" -> 5.0f;
            case "witch" -> 6.0f;
            case "pillager" -> 7.0f;
            default -> 0.0f;
        };
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

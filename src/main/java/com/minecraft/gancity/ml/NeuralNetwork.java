package com.minecraft.gancity.ml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Simple feedforward neural network - Pure Java implementation
 * 2-layer network with ReLU activation
 */
public class NeuralNetwork implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final int inputSize;
    private final int hiddenSize;
    private final int outputSize;
    private final float learningRate;
    
    // Layer 1: input -> hidden
    private float[][] weights1;
    private float[] biases1;
    
    // Layer 2: hidden -> output  
    private float[][] weights2;
    private float[] biases2;
    
    // Cached activations for backprop
    private transient float[] hiddenActivations;
    
    private Random random;
    
    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize, float learningRate) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.outputSize = outputSize;
        this.learningRate = learningRate;
        this.random = new Random();
        
        initializeWeights();
    }
    
    private void initializeWeights() {
        // Xavier/Glorot initialization
        float scale1 = (float) Math.sqrt(2.0 / inputSize);
        float scale2 = (float) Math.sqrt(2.0 / hiddenSize);
        
        weights1 = new float[inputSize][hiddenSize];
        biases1 = new float[hiddenSize];
        
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weights1[i][j] = (random.nextFloat() - 0.5f) * 2 * scale1;
            }
        }
        
        weights2 = new float[hiddenSize][outputSize];
        biases2 = new float[outputSize];
        
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                weights2[i][j] = (random.nextFloat() - 0.5f) * 2 * scale2;
            }
        }
    }
    
    /**
     * Forward pass
     */
    public float[] forward(float[] input) {
        if (input.length != inputSize) {
            throw new IllegalArgumentException("Input size mismatch");
        }
        
        // Layer 1: input -> hidden (with ReLU)
        hiddenActivations = new float[hiddenSize];
        for (int j = 0; j < hiddenSize; j++) {
            float sum = biases1[j];
            for (int i = 0; i < inputSize; i++) {
                sum += input[i] * weights1[i][j];
            }
            hiddenActivations[j] = relu(sum);
        }
        
        // Layer 2: hidden -> output
        float[] output = new float[outputSize];
        for (int j = 0; j < outputSize; j++) {
            float sum = biases2[j];
            for (int i = 0; i < hiddenSize; i++) {
                sum += hiddenActivations[i] * weights2[i][j];
            }
            output[j] = sum;  // No activation on output layer for Q-values
        }
        
        return output;
    }
    
    /**
     * Train on single sample using backpropagation
     */
    public void train(float[] input, float[] target) {
        // Forward pass
        float[] output = forward(input);
        
        // Compute output layer gradients (MSE loss)
        float[] outputGradients = new float[outputSize];
        for (int i = 0; i < outputSize; i++) {
            outputGradients[i] = 2 * (output[i] - target[i]);
        }
        
        // Backprop to hidden layer
        float[] hiddenGradients = new float[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            float sum = 0;
            for (int j = 0; j < outputSize; j++) {
                sum += outputGradients[j] * weights2[i][j];
            }
            // ReLU derivative
            hiddenGradients[i] = hiddenActivations[i] > 0 ? sum : 0;
        }
        
        // Update weights and biases (layer 2)
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                weights2[i][j] -= learningRate * outputGradients[j] * hiddenActivations[i];
            }
        }
        for (int i = 0; i < outputSize; i++) {
            biases2[i] -= learningRate * outputGradients[i];
        }
        
        // Update weights and biases (layer 1)
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weights1[i][j] -= learningRate * hiddenGradients[j] * input[i];
            }
        }
        for (int i = 0; i < hiddenSize; i++) {
            biases1[i] -= learningRate * hiddenGradients[i];
        }
    }
    
    /**
     * Copy weights from another network
     */
    public void copyWeightsFrom(NeuralNetwork other) {
        for (int i = 0; i < inputSize; i++) {
            System.arraycopy(other.weights1[i], 0, this.weights1[i], 0, hiddenSize);
        }
        System.arraycopy(other.biases1, 0, this.biases1, 0, hiddenSize);
        
        for (int i = 0; i < hiddenSize; i++) {
            System.arraycopy(other.weights2[i], 0, this.weights2[i], 0, outputSize);
        }
        System.arraycopy(other.biases2, 0, this.biases2, 0, outputSize);
    }
    
    private float relu(float x) {
        return Math.max(0, x);
    }
    
    public void save(Path path) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(path))) {
            oos.writeObject(this);
        }
    }
    
    public void load(Path path) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(path))) {
            NeuralNetwork loaded = (NeuralNetwork) ois.readObject();
            copyWeightsFrom(loaded);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load network", e);
        }
    }
}

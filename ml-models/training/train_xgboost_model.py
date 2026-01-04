"""
XGBoost Fraud Detection Model Training
======================================

This script trains an XGBoost model for fraud detection using synthetic transaction data.
The model will be used for real-time inference in the Flink fraud detection pipeline.

Features:
- Transaction amount, frequency, location, device info
- Velocity patterns, geo-velocity anomalies
- Merchant reputation, customer history

Output:
- Trained XGBoost model (saved as .pkl or .onnx)
- Model metrics and evaluation
- Feature importance analysis
"""

import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score
import xgboost as xgb
import joblib
import json
from datetime import datetime
import optuna  # For Bayesian optimization
import warnings
warnings.filterwarnings('ignore')

# Configuration
RANDOM_STATE = 42
TEST_SIZE = 0.2
MODEL_OUTPUT_DIR = "../models"
METRICS_OUTPUT_DIR = "../metrics"

def generate_synthetic_training_data(n_samples=100000):
    """
    Generate synthetic transaction data for training.
    In production, this would come from historical transaction data.
    """
    print(f"Generating {n_samples} synthetic transactions...")
    
    np.random.seed(RANDOM_STATE)
    
    # Feature generation
    data = {
        'transaction_amount': np.random.lognormal(mean=4.0, sigma=1.5, size=n_samples),
        'transaction_count_1min': np.random.poisson(lam=2, size=n_samples),
        'transaction_count_1hour': np.random.poisson(lam=10, size=n_samples),
        'total_amount_1min': np.random.lognormal(mean=5.0, sigma=1.0, size=n_samples),
        'total_amount_1hour': np.random.lognormal(mean=6.5, sigma=1.2, size=n_samples),
        'distance_from_home_km': np.random.exponential(scale=50, size=n_samples),
        'time_since_last_txn_minutes': np.random.exponential(scale=30, size=n_samples),
        'velocity_kmh': np.random.exponential(scale=100, size=n_samples),
        'merchant_reputation_score': np.random.beta(a=2, b=2, size=n_samples),
        'customer_age_days': np.random.exponential(scale=365, size=n_samples),
        'is_known_device': np.random.choice([0, 1], size=n_samples, p=[0.2, 0.8]),
        'is_vpn_detected': np.random.choice([0, 1], size=n_samples, p=[0.95, 0.05]),
        'device_usage_count': np.random.poisson(lam=5, size=n_samples),
        'hour_of_day': np.random.randint(0, 24, size=n_samples),
        'day_of_week': np.random.randint(0, 7, size=n_samples),
        'merchant_category_risk': np.random.choice([0, 1, 2], size=n_samples, p=[0.7, 0.25, 0.05]),
    }
    
    df = pd.DataFrame(data)
    
    # Create fraud labels based on patterns
    # Fraud indicators:
    # - High velocity (many transactions in short time)
    # - High amounts
    # - Impossible travel (high velocity_kmh)
    # - Unknown device + VPN
    # - High-risk merchant category
    
    fraud_score = (
        (df['transaction_count_1min'] > 5).astype(int) * 0.3 +
        (df['total_amount_1min'] > 2000).astype(int) * 0.3 +
        (df['velocity_kmh'] > 800).astype(int) * 0.2 +
        ((df['is_known_device'] == 0) & (df['is_vpn_detected'] == 1)).astype(int) * 0.1 +
        (df['merchant_category_risk'] == 2).astype(int) * 0.1
    )
    
    # Add some noise
    fraud_score += np.random.normal(0, 0.1, size=n_samples)
    
    # Create binary labels (fraud = 1, normal = 0)
    df['is_fraud'] = (fraud_score > 0.5).astype(int)
    
    # Ensure ~5% fraud rate (realistic)
    fraud_rate = df['is_fraud'].mean()
    if fraud_rate < 0.03:
        threshold = np.percentile(fraud_score, 97)
        df['is_fraud'] = (fraud_score > threshold).astype(int)
    elif fraud_rate > 0.10:
        threshold = np.percentile(fraud_score, 90)
        df['is_fraud'] = (fraud_score > threshold).astype(int)
    
    print(f"Fraud rate: {df['is_fraud'].mean():.2%}")
    return df

def prepare_features(df):
    """Prepare features for model training."""
    feature_columns = [
        'transaction_amount',
        'transaction_count_1min',
        'transaction_count_1hour',
        'total_amount_1min',
        'total_amount_1hour',
        'distance_from_home_km',
        'time_since_last_txn_minutes',
        'velocity_kmh',
        'merchant_reputation_score',
        'customer_age_days',
        'is_known_device',
        'is_vpn_detected',
        'device_usage_count',
        'hour_of_day',
        'day_of_week',
        'merchant_category_risk',
    ]
    
    X = df[feature_columns].copy()
    y = df['is_fraud'].copy()
    
    return X, y, feature_columns

def train_baseline_model(X_train, y_train, X_val, y_val):
    """Train baseline XGBoost model."""
    print("\nTraining baseline XGBoost model...")
    
    model = xgb.XGBClassifier(
        n_estimators=100,
        max_depth=6,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=RANDOM_STATE,
        eval_metric='auc',
        use_label_encoder=False
    )
    
    model.fit(
        X_train, y_train,
        eval_set=[(X_val, y_val)],
        verbose=False
    )
    
    return model

def optimize_hyperparameters(X_train, y_train, X_val, y_val, n_trials=50):
    """
    Use Bayesian optimization (Optuna) to find best hyperparameters.
    """
    print(f"\nRunning Bayesian optimization ({n_trials} trials)...")
    
    def objective(trial):
        params = {
            'n_estimators': trial.suggest_int('n_estimators', 50, 300),
            'max_depth': trial.suggest_int('max_depth', 3, 10),
            'learning_rate': trial.suggest_float('learning_rate', 0.01, 0.3, log=True),
            'subsample': trial.suggest_float('subsample', 0.6, 1.0),
            'colsample_bytree': trial.suggest_float('colsample_bytree', 0.6, 1.0),
            'min_child_weight': trial.suggest_int('min_child_weight', 1, 10),
            'gamma': trial.suggest_float('gamma', 0, 0.5),
            'reg_alpha': trial.suggest_float('reg_alpha', 0, 1),
            'reg_lambda': trial.suggest_float('reg_lambda', 0, 1),
            'random_state': RANDOM_STATE,
            'eval_metric': 'auc',
            'use_label_encoder': False
        }
        
        model = xgb.XGBClassifier(**params)
        model.fit(
            X_train, y_train,
            eval_set=[(X_val, y_val)],
            verbose=False
        )
        
        y_pred_proba = model.predict_proba(X_val)[:, 1]
        auc_score = roc_auc_score(y_val, y_pred_proba)
        
        return auc_score
    
    study = optuna.create_study(direction='maximize', study_name='xgboost_fraud_detection')
    study.optimize(objective, n_trials=n_trials, show_progress_bar=True)
    
    print(f"\nBest hyperparameters: {study.best_params}")
    print(f"Best AUC score: {study.best_value:.4f}")
    
    # Train final model with best parameters
    best_params = study.best_params.copy()
    best_params['random_state'] = RANDOM_STATE
    best_params['eval_metric'] = 'auc'
    best_params['use_label_encoder'] = False
    
    final_model = xgb.XGBClassifier(**best_params)
    final_model.fit(
        X_train, y_train,
        eval_set=[(X_val, y_val)],
        verbose=False
    )
    
    return final_model, study.best_params

def evaluate_model(model, X_test, y_test):
    """Evaluate model performance."""
    print("\nEvaluating model...")
    
    y_pred = model.predict(X_test)
    y_pred_proba = model.predict_proba(X_test)[:, 1]
    
    # Metrics
    auc_score = roc_auc_score(y_test, y_pred_proba)
    report = classification_report(y_test, y_pred, output_dict=True)
    cm = confusion_matrix(y_test, y_pred)
    
    print(f"\nAUC Score: {auc_score:.4f}")
    print(f"\nClassification Report:")
    print(classification_report(y_test, y_pred))
    print(f"\nConfusion Matrix:")
    print(cm)
    
    # Calculate false positive rate
    tn, fp, fn, tp = cm.ravel()
    fpr = fp / (fp + tn) if (fp + tn) > 0 else 0
    print(f"\nFalse Positive Rate: {fpr:.4f}")
    
    return {
        'auc_score': float(auc_score),
        'classification_report': report,
        'confusion_matrix': cm.tolist(),
        'false_positive_rate': float(fpr),
        'true_positives': int(tp),
        'false_positives': int(fp),
        'true_negatives': int(tn),
        'false_negatives': int(fn)
    }

def save_model(model, feature_columns, metrics, best_params=None):
    """Save model and metadata."""
    import os
    os.makedirs(MODEL_OUTPUT_DIR, exist_ok=True)
    os.makedirs(METRICS_OUTPUT_DIR, exist_ok=True)
    
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    
    # Save model
    model_path = f"{MODEL_OUTPUT_DIR}/xgboost_fraud_model_{timestamp}.pkl"
    joblib.dump(model, model_path)
    print(f"\nModel saved to: {model_path}")
    
    # Save metadata
    metadata = {
        'model_type': 'XGBoost',
        'timestamp': timestamp,
        'feature_columns': feature_columns,
        'metrics': metrics,
        'best_hyperparameters': best_params,
        'model_path': model_path
    }
    
    metadata_path = f"{METRICS_OUTPUT_DIR}/model_metadata_{timestamp}.json"
    with open(metadata_path, 'w') as f:
        json.dump(metadata, f, indent=2, default=str)
    
    print(f"Metadata saved to: {metadata_path}")
    
    return model_path, metadata_path

def main():
    """Main training pipeline."""
    print("=" * 60)
    print("XGBoost Fraud Detection Model Training")
    print("=" * 60)
    
    # Step 1: Generate training data
    df = generate_synthetic_training_data(n_samples=100000)
    
    # Step 2: Prepare features
    X, y, feature_columns = prepare_features(df)
    
    # Step 3: Split data
    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.3, random_state=RANDOM_STATE, stratify=y
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.5, random_state=RANDOM_STATE, stratify=y_temp
    )
    
    print(f"\nTraining set: {len(X_train)} samples")
    print(f"Validation set: {len(X_val)} samples")
    print(f"Test set: {len(X_test)} samples")
    
    # Step 4: Train baseline model
    baseline_model = train_baseline_model(X_train, y_train, X_val, y_val)
    baseline_metrics = evaluate_model(baseline_model, X_test, y_test)
    
    # Step 5: Optimize hyperparameters
    optimized_model, best_params = optimize_hyperparameters(
        X_train, y_train, X_val, y_val, n_trials=50
    )
    optimized_metrics = evaluate_model(optimized_model, X_test, y_test)
    
    # Step 6: Compare models
    print("\n" + "=" * 60)
    print("Model Comparison")
    print("=" * 60)
    print(f"Baseline AUC: {baseline_metrics['auc_score']:.4f}")
    print(f"Optimized AUC: {optimized_metrics['auc_score']:.4f}")
    improvement = optimized_metrics['auc_score'] - baseline_metrics['auc_score']
    print(f"Improvement: {improvement:.4f} ({improvement/baseline_metrics['auc_score']*100:.2f}%)")
    
    print(f"\nBaseline FPR: {baseline_metrics['false_positive_rate']:.4f}")
    print(f"Optimized FPR: {optimized_metrics['false_positive_rate']:.4f}")
    fpr_reduction = baseline_metrics['false_positive_rate'] - optimized_metrics['false_positive_rate']
    fpr_reduction_pct = (fpr_reduction / baseline_metrics['false_positive_rate'] * 100) if baseline_metrics['false_positive_rate'] > 0 else 0
    print(f"FPR Reduction: {fpr_reduction:.4f} ({fpr_reduction_pct:.2f}%)")
    
    # Step 7: Save best model
    model_path, metadata_path = save_model(
        optimized_model, 
        feature_columns, 
        optimized_metrics,
        best_params
    )
    
    # Feature importance
    print("\n" + "=" * 60)
    print("Top 10 Feature Importance")
    print("=" * 60)
    feature_importance = pd.DataFrame({
        'feature': feature_columns,
        'importance': optimized_model.feature_importances_
    }).sort_values('importance', ascending=False)
    print(feature_importance.head(10).to_string(index=False))
    
    print("\n" + "=" * 60)
    print("Training Complete!")
    print("=" * 60)
    print(f"Model: {model_path}")
    print(f"Metadata: {metadata_path}")
    
    return optimized_model, optimized_metrics

if __name__ == "__main__":
    model, metrics = main()

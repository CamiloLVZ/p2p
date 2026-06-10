# Documentación IdentificacionGenerosMusicales - Clasificador de Géneros Musicales

## Descripción General

Sistema completo de Machine Learning que clasifica audios WAV en 10 géneros musicales (blues, classical, country, disco, hiphop, jazz, metal, pop, reggae, rock). Incluye entrenamiento de red neuronal, API REST y evaluación completa.

**Arquitectura general:**

```
Audios WAV (dataset GTZAN)
     ↓
[ENTRENAMIENTO OFFLINE]
training/train.py ejecuta:
  - Carga 1000 canciones
  - Divide en segmentos de 4 segundos
  - Crea Mel Spectrogram (representación visual del audio)
  - Data augmentation (ruido, pitch shift, time stretch, etc)
  - Entrena CNN10 en 2 fases: Transfer Learning + Fine-tuning
     ↓
[ARTEFACTOS GUARDADOS]
  - best_model.keras (red neuronal entrenada)
  - label_encoder.pkl (mapeo de géneros a números)
  - preprocessing_config.json (parámetros de audio)
     ↓
[BACKEND API - FastAPI]
  - GET /health (¿modelo cargado?)
  - GET /genres (lista de géneros)
  - POST /predict (clasificar WAV)
     ↓
[PREDICCIÓN]
  - Recibe bytes de WAV
  - Extrae features (Mel Spectrogram)
  - Predice con modelo
  - Retorna JSON con género y probabilidades
```

**Stack tecnológico:**

- Entrenamiento: Python 3.10+, TensorFlow/Keras 3, librosa, scikit-learn, numpy
- Backend: FastAPI, Uvicorn, Pydantic
- Audio: librosa (carga/procesamiento), scipy (transformada de Fourier)
- Modelo: CNN10 (arquitectura de PANNs - Pre-trained Audio Neural Networks)

---

## PARTE 1: ENTRENAMIENTO

### `training/config.py` - Configuración Centralizada

Archivo que define TODOS los parámetros del entrenamiento en un solo lugar:

```python
# Rutas
TRAINING_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = TRAINING_DIR.parent
DATASET_PATH = PROJECT_ROOT / "dataset"
MODELS_DIR = PROJECT_ROOT / "models"
CACHE_DIR = PROJECT_ROOT / "cache"  # Espectrogramas precalculados

# Audio - Específico de PANNs CNN10
SAMPLE_RATE = 32000              # Muestreo en Hz (32 kHz)
N_MELS = 64                       # Bandas de frecuencia Mel (64 bandas)
N_FFT = 1024                      # Tamaño de ventana FFT
HOP_LENGTH = 320                  # Salto entre frames
FMIN = 50, FMAX = 14000          # Rango de frecuencias (50-14000 Hz)

# Segmentación
MAX_AUDIO_DURATION_SECONDS = 30   # Máximo de 30s por audio
SEGMENT_DURATION_SECONDS = 4.0    # Cada segmento dura 4 segundos
SEGMENT_OVERLAP = 0.5             # 50% de superposición entre segmentos
SILENCE_THRESHOLD = 0.0015        # Umbral para detectar silencio

# Entrenamiento
BATCH_SIZE = 16                   # Fase 1: Transfer Learning
FINE_TUNE_BATCH_SIZE = 8          # Fase 2: Fine-tuning (batch menor)
INITIAL_EPOCHS = 10               # Fase 1: 10 épocas
FINE_TUNE_EPOCHS = 30             # Fase 2: 30 épocas más
INITIAL_LEARNING_RATE = 1e-3      # Learning rate fase 1
FINE_TUNE_LEARNING_RATE = 3e-5    # Learning rate fase 2 (más bajo)
```

**¿Por qué 2 learning rates?**

- Fase 1: Learning rate alto (1e-3) porque solo entrenam nuevo clasificador
- Fase 2: Learning rate muy bajo (3e-5) porque ajustamos toda la red (riesgo de olvidar conocimiento preentrenado)

---

### `training/preprocessing.py` - Conversión de Audio a Features

Responsable de convertir archivos WAV crudos en matriz numérica que la red entiende.

**Concepto clave: Mel Spectrogram**

Un audio es una onda de amplitud en el tiempo. Para que una CNN lo procese, necesita una "imagen":

```
Audio WAV (onda 1D)
     ↓ FFT (Transformada de Fourier)
Espectrogram (matriz 2D: frecuencia × tiempo)
     ↓ Escala Mel (frecuencias percibidas por humanos)
Mel Spectrogram (64 bandas × tiempo × 1)
     ↓ Normalización (resta media, divide por desv estándar)
Input listo para CNN
```

**Funciones principales:**

| Función                                                   | Qué hace                                                                 |
| --------------------------------------------------------- | ------------------------------------------------------------------------ |
| `load_audio(file_path, sr, duration)`                     | Carga WAV con librosa, lo convierte a mono, resamplea a sample_rate fija |
| `split_audio_into_segments(audio, sr, duration, overlap)` | Divide un audio largo en segmentos de 4s con 50% overlap                 |
| `audio_to_features(audio, sr, feature_type, duration)`    | Calcula Mel Spectrogram: FFT → Mel scale → normalización                 |
| `load_or_compute_segments(file_path, cache_dir, ...)`     | Intenta cargar desde caché; si no existe, calcula y guarda               |
| `get_dataset_files(dataset_path)`                         | Recorre dataset/, retorna lista de (archivo_wav, género)                 |
| `is_audio_silent(file_path, threshold)`                   | Detecta audios vacíos/silenciosos por RMS (Root Mean Square)             |

**Ejemplo práctico: Procesar un audio**

```python
# Entrada: archivo blues.00000.wav (30 segundos)
audio, sr = load_audio("blues.00000.wav", sample_rate=32000)
# audio.shape = (960000,)  → 30s × 32000 Hz = 960000 muestras

# Dividir en segmentos de 4 segundos con 50% overlap
# 30s → 14 segmentos de 4s
segments = split_audio_into_segments(audio, sr=32000, duration=4.0, overlap=0.5)
# len(segments) = 14

# Convertir cada segmento a Mel Spectrogram
features = []
for seg in segments:
    feat = audio_to_features(seg, sr=32000, feature_type="mel", duration=4.0)
    # feat.shape = (64, 128, 1)
    #   64 bandas Mel
    #   128 pasos temporales (≈ 4 segundos)
    #   1 canal
    features.append(feat)

# Resultado: matriz (14, 64, 128, 1) → 14 imágenes de 64×128 píxeles
```

**Sistema de Caché**

Para evitar recalcular Mel Spectrograms en cada entrenamiento:

```
cache/
├── mel_sr32000_dur4.0s_ov0.5/
│   ├── blues/
│   │   ├── blues.00000.npy  (14, 64, 128, 1)
│   │   ├── blues.00001.npy
│   │   └── ...
│   ├── classical/
│   └── ...
```

- Primer entrenamiento: calcula todos los spectrograms, los guarda como .npy
- Siguientes entrenamientos: carga directamente desde caché (mucho más rápido)
- Si cambias parámetros (sample_rate, duration), crea carpeta nueva automáticamente

---

### `training/augmentations.py` - Data Augmentation

Genera variaciones de los audios para entrenar un modelo más robusto. Se aplica SOLO en training set, no en validación/test.

**Técnicas aplicadas:**

| Técnica          | Implementación                                     | Efecto                       | Probabilidad |
| ---------------- | -------------------------------------------------- | ---------------------------- | ------------ |
| **White Noise**  | Añade ruido gaussiano aleatorio                    | Simula ambientes ruidosos    | 40%          |
| **Time Shift**   | Desplaza el audio circularmente                    | Robustez a posición temporal | 40%          |
| **Random Gain**  | Multiplica amplitud por factor aleatorio (0.7-1.3) | Simula diferentes volúmenes  | 50%          |
| **Time Stretch** | Acelera/ralentiza sin cambiar pitch (librosa)      | Robustez a cambios de BPM    | 30%          |
| **Pitch Shift**  | Sube/baja tonalidad sin cambiar duración           | Robustez a transposiciones   | 35%          |

**Función `augment_audio(audio, sample_rate)`:**

```python
def augment_audio(audio: np.ndarray, sample_rate: int) -> np.ndarray:
    augmented = audio.copy()

    if random.random() < 0.4:  # 40% probabilidad
        augmented = add_white_noise(augmented)

    if random.random() < 0.4:
        augmented = time_shift(augmented)

    if random.random() < 0.5:
        augmented = random_gain(augmented)

    if random.random() < 0.3:
        augmented = time_stretch(augmented)

    if random.random() < 0.35:
        augmented = pitch_shift(augmented, sample_rate)

    return augmented
```

**Impacto en los datos:**

```
Original: 700 audios × 14 segmentos = 9,800 muestras
     ↓
Con augmentation:
  - Cada segmento original: 1 copia
  - + Cada segmento aumentado: 1 copia
  = 9,800 × 2 = 19,600 muestras para entrenamiento
```

Duplica efectivamente el tamaño del dataset de entrenamiento sin necesidad de más datos reales.

---

### `training/panns_cnn10.py` - Arquitectura de la Red Neuronal

**¿Qué es PANNs?**

- **P**re-trained **A**udio **N**eural **N**etworks
- Redes entrenadas en AudioSet: 2 millones de clips de YouTube con 527 categorías
- CNN10: versión ligera diseñada para GPUs con poca VRAM (como GTX 1650)

**Arquitectura CNN10:**

```
Input: (64, 128, 1)  [64 bandas Mel, 128 pasos temporales, 1 canal]
   ↓
Conv2D(3×3, 64 filtros) → BN → ReLU → Dropout(0.2) → MaxPool(2×2)  [output: 32×64×64]
   ↓
Conv2D(3×3, 128 filtros) → BN → ReLU → Dropout(0.2) → MaxPool(2×2)  [output: 16×32×128]
   ↓
Conv2D(3×3, 256 filtros) → BN → ReLU → Dropout(0.2) → MaxPool(2×2)  [output: 8×16×256]
   ↓
Conv2D(3×3, 512 filtros) → BN → ReLU → Dropout(0.2) → MaxPool(2×2)  [output: 4×8×512]
   ↓
AudioPooling:
  - Reduce dimension de frecuencia (promedio sobre bandas Mel)
  - Max pooling + avg pooling sobre tiempo
  - Suma ambos resultados
  [output: 512]
   ↓
Dropout(0.5) → Dense(512, relu) → Dropout(0.5)
   ↓
Dense(10, softmax)  [probabilidad para cada género]
```

**Componentes clave:**

| Componente             | Propósito                                                       |
| ---------------------- | --------------------------------------------------------------- |
| **Conv2D**             | Detecta patrones visuales en Mel Spectrogram (edges, texturas)  |
| **BatchNormalization** | Normaliza salida de capas, acelera entrenamiento                |
| **ReLU**               | Función de activación no-lineal (introduce expresividad)        |
| **Dropout**            | Desactiva neuronas al azar (~regularización, evita overfitting) |
| **MaxPool**            | Reduce dimensión, mantiene rasgos importantes                   |
| **AudioPooling**       | Capa custom que reduce frecuencia + tiempo de forma balanceada  |
| **Dense**              | Capas fully-connected para clasificación final                  |
| **Softmax**            | Convierte scores en probabilidades (suman 1)                    |

**Carga de pesos preentrenados:**

```python
def load_panns_weights(model, weights_npz_path):
    # Carga pesos de AudioSet en:
    # - Conv blocks (conv_block1-4)
    # - FC layer (fc1)
    # NO carga 'predictions' (cabeza de clasificación) porque:
    # - AudioSet tiene 527 clases
    # - GTZAN tiene 10 clases
    # - Se entrena 'predictions' desde cero
```

**Funciones de congelamiento:**

```python
def freeze_backbone(model):
    # Congela: todos los convolutions + fc1
    # Entrenable: solo capa 'predictions'
    # Usado en FASE 1: Transfer Learning

def unfreeze_all(model):
    # Descongela todas las capas
    # Usado en FASE 2: Fine-tuning
```

---

### `training/train.py` - Orquestación Completa

Script principal que orquesta todo el entrenamiento. Ejecución:

```bash
python training/train.py --dataset_path dataset/ --epochs 10 --fine_tune_epochs 30 --batch_size 16
```

**Flujo paso a paso:**

**1. Configuración inicial**

```python
configure_gpu()          # Activa memory_growth para evitar OOM
set_random_seed(42)     # Reproducibilidad
create_directories(...)  # Crea carpetas models/, outputs/, cache/
```

**2. Carga de dataset**

```python
audio_files, labels = get_dataset_files("dataset/")
# Retorna: (["blues/blues.00000.wav", ...], ["blues", ...])
```

**3. Filtrado de audios silenciosos**

```python
for file_path, label in zip(audio_files, labels):
    if is_audio_silent(file_path):
        # Ignorar archivos vacíos
    else:
        # Guardar para entrenamiento
```

**4. Codificación de etiquetas**

```python
label_encoder = LabelEncoder()
label_encoder.fit(labels)  # blues→0, classical→1, ..., rock→9
# Guardar en models/label_encoder.pkl para predicción después
```

**5. División estratificada (no mezclar segmentos del mismo audio)**

```python
# IMPORTANTE: Dividimos a nivel de ARCHIVO, no segmento
# Esto evita "data leakage": segmentos de la misma canción en train y test

files_train, files_temp, labels_train, labels_temp = train_test_split(
    audio_files, labels,
    test_size=0.30,        # 30% de archivos para validación + test
    stratify=labels        # Mantener proporción de géneros
)

files_val, files_test, labels_val, labels_test = train_test_split(
    files_temp, labels_temp,
    test_size=0.50,        # 50% de temp (15% del total) para test
    stratify=labels_temp   # Mantener proporción
)

# Resultado: 70% train, 15% val, 15% test
```

**6. Preprocesamiento**

```python
X_train, y_train = load_and_preprocess_dataset(
    files_train, labels_train,
    augment=True   # Aplicar aumento de datos en entrenamiento
)
# X_train.shape: (19600, 64, 128, 1)  [9800 segmentos × 2 por augmentation]
# y_train.shape: (19600,)              [etiqueta para cada segmento]

X_val, y_val = load_and_preprocess_dataset(
    files_val, labels_val,
    augment=False  # Sin augmentation en validación
)
# X_val.shape: (2100, 64, 128, 1)
# y_val.shape: (2100,)
```

**7. Construcción del modelo**

```python
input_shape = (64, 128, 1)  # N_MELS, expected_time_steps, 1 canal
model = build_cnn10(input_shape, num_classes=10)
load_panns_weights(model, "models/panns_cnn10_weights.npz")
# Cargar pesos preentrenados de AudioSet
```

**8. FASE 1: Transfer Learning (10 épocas)**

```python
freeze_backbone(model)  # Congela todas las capas excepto 'predictions'

model.compile(
    optimizer=Adam(learning_rate=1e-3),
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"]
)

callbacks = [
    EarlyStopping(monitor="val_accuracy", patience=5),
    ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=3)
]

history = model.fit(
    GenreDataset(X_train, y_train, batch_size=16, shuffle=True),
    validation_data=GenreDataset(X_val, y_val, batch_size=16),
    epochs=10,
    callbacks=callbacks
)
```

**¿Qué ocurre?**

- Modelo procesa lotes de 16 segmentos
- Cada segmento: (64, 128, 1) → features CNN → vectores 512-dim → Dense(10) → softmax
- Loss: sparse_categorical_crossentropy (penaliza predicciones incorrectas)
- Early stopping: detiene si val_accuracy no mejora 5 épocas
- ReduceLROnPlateau: reduce learning rate si loss no mejora

**9. FASE 2: Fine-tuning (30 épocas más)**

```python
unfreeze_all(model)  # Descongela todas las capas

model.compile(
    optimizer=Adam(learning_rate=3e-5),  # Learning rate MÁS BAJO
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"]
)

# Similar a Fase 1 pero con batch_size=8 (menor) y learning rate 3e-5
```

**¿Por qué 2 fases?**

- Fase 1: Rápido + seguro (solo entrenar clasificador nuevo)
- Fase 2: Fino ajuste (adaptar features preentrenadas a GTZAN)

**10. Evaluación en Test Set**

```python
# Crucial: evaluar a nivel de ARCHIVO (promediando segmentos)
# No a nivel de segmento individual

for file_path, true_label in zip(files_test, labels_test):
    segments = process_audio_file(file_path)
    probs = model.predict(segments)  # (N_seg, 10)

    mean_probs = np.mean(probs, axis=0)  # Promediar sobre segmentos
    predicted_label = argmax(mean_probs)

    y_test_true.append(true_label)
    y_test_pred.append(predicted_label)

# Generar reporte
print(classification_report(y_test_true, y_test_pred))
```

**11. Guardar artefactos**

```python
model.save("models/best_model.keras")

preprocessing_config = {
    "feature_type": "mel",
    "segment_duration": 4.0,
    "overlap": 0.5
}
with open("models/preprocessing_config.json", "w") as f:
    json.dump(preprocessing_config, f)
```

**Clase GenreDataset (custom):**

```python
class GenreDataset(keras.utils.PyDataset):
    def __init__(self, X, y, batch_size, shuffle=False):
        self.X = X
        self.y = y
        self.batch_size = batch_size
        self.shuffle = shuffle
        self.indices = np.arange(len(X))

    def __len__(self):
        return ceil(len(X) / batch_size)  # Número de batches

    def __getitem__(self, idx):
        start = idx * batch_size
        end = min(start + batch_size, len(X))
        batch_indices = self.indices[start:end]
        return self.X[batch_indices], self.y[batch_indices]

    def on_epoch_end(self):
        if self.shuffle:
            np.random.shuffle(self.indices)  # Mezclar después de cada época
```

Ventaja: carga datos en memoria sin duplicación (a diferencia de cargar todo el dataset en RAM al inicio).

---

## PARTE 2: BACKEND API

### Arquitectura General

```
HTTP Request (POST /predict con WAV)
         ↓
FastAPI server (app/factory.py)
         ↓
routes.py: endpoint /predict
         ↓ inyecta (Depends)
get_prediction_service()
         ↓
KerasGenrePredictionService
         ↓
infrastructure/keras_model_repository.py
    - Carga best_model.keras
    - Carga label_encoder.pkl
    - Carga preprocessing_config.json
         ↓
infrastructure/audio_feature_extractor.py
    - Extrae Mel Spectrogram del WAV
         ↓
model.predict()
    - Retorna probabilidades (1, 10)
         ↓
PredictionResult
    - predicted_genre
    - confidence
    - probabilities por cada género
         ↓
HTTP Response (JSON)
```

---

### `backend/app/core/config.py` - Configuración

```python
@dataclass(frozen=True)
class Settings:
    project_root: Path
    model_path: Path                    # ../../models/best_model.keras
    label_encoder_path: Path            # ../../models/label_encoder.pkl
    preprocessing_config_path: Path     # ../../models/preprocessing_config.json
    app_title: str = "GTZAN Music Genre Classifier"
    app_version: str = "1.0.0"

    @classmethod
    def from_project_root(cls):
        # Auto-detecta rutas
        project_root = Path(__file__).resolve().parents[3]
        return cls(
            project_root=project_root,
            model_path=project_root / "models" / "best_model.keras",
            ...
        )
```

---

### `backend/app/core/container.py` - Inyección de Dependencias

```python
def build_prediction_service() -> KerasGenrePredictionService:
    """Construye el servicio con sus dependencias."""
    settings = Settings.from_project_root()

    return KerasGenrePredictionService(
        model_repository=KerasModelRepository(settings),
        feature_extractor=TrainingAudioFeatureExtractor(settings.project_root)
    )
```

Se llama una sola vez al startup de FastAPI.

---

### `backend/app/factory.py` - Crea la Aplicación FastAPI

```python
def create_app() -> FastAPI:
    settings = Settings.from_project_root()

    app = FastAPI(
        title="GTZAN Music Genre Classifier",
        version="1.0.0"
    )

    # Construir servicio y guardarlo en app.state
    app.state.prediction_service = build_prediction_service()

    # Evento: cuando FastAPI inicia
    @app.on_event("startup")
    def startup_event():
        app.state.prediction_service.load()  # Cargar modelo a memoria

    app.include_router(router)  # Registrar rutas
    return app
```

**Flujo de startup:**

```
python -m uvicorn main:app
  ↓
FastAPI crea instancia
  ↓
Llama build_prediction_service()
  ↓
Evento "startup" → prediction_service.load()
  ↓
Carga best_model.keras en memoria (GPU si disponible)
  ↓
API lista para recibir peticiones
```

---

### `backend/app/api/routes.py` - Endpoints HTTP

**GET `/` - Verificar que API está activa**

```python
@router.get("/", response_model=HomeResponse)
def home() -> HomeResponse:
    return HomeResponse(
        message="API de clasificacion de generos musicales funcionando"
    )
```

Respuesta:

```json
{ "message": "API de clasificacion de generos musicales funcionando" }
```

**GET `/health` - Status del modelo**

```python
@router.get("/health", response_model=HealthResponse)
def health(service: GenrePredictionService = Depends(...)) -> HealthResponse:
    ready = service.is_ready()
    return HealthResponse(
        status="ok" if ready else "error",
        model_loaded=ready
    )
```

Respuesta (modelo cargado):

```json
{ "status": "ok", "model_loaded": true }
```

**GET `/genres` - Lista de géneros**

```python
@router.get("/genres", response_model=GenresResponse)
def genres(service: GenrePredictionService = Depends(...)) -> GenresResponse:
    return GenresResponse(genres=service.get_genres())
```

Respuesta:

```json
{
  "genres": [
    "blues",
    "classical",
    "country",
    "disco",
    "hiphop",
    "jazz",
    "metal",
    "pop",
    "reggae",
    "rock"
  ]
}
```

**POST `/predict` - Clasificar WAV**

```python
@router.post("/predict", response_model=PredictionResponse)
async def predict(
    file: UploadFile = File(...),
    service: GenrePredictionService = Depends(...)
) -> PredictionResponse:
    # 1. Validar que es WAV
    if not file.filename.lower().endswith(".wav"):
        raise HTTPException(status_code=400, detail="Debe ser WAV")

    # 2. Leer bytes
    wav_bytes = await file.read()

    # 3. Predecir
    prediction = service.predict(wav_bytes)

    # 4. Retornar resultado
    return PredictionResponse(**prediction.to_dict())
```

Respuesta (ejemplo):

```json
{
  "predicted_genre": "rock",
  "confidence": 0.91,
  "probabilities": {
    "blues": 0.01,
    "classical": 0.0,
    "country": 0.02,
    "disco": 0.01,
    "hiphop": 0.01,
    "jazz": 0.0,
    "metal": 0.01,
    "pop": 0.01,
    "reggae": 0.01,
    "rock": 0.91
  }
}
```

---

### `backend/app/domain/` - Contratos e Interfaces

**domain/models.py - Clases de Dominio**

```python
@dataclass(frozen=True)
class PreprocessingConfig:
    feature_type: str = "mel"
    segment_duration: float = 10.0
    overlap: float = 0.0

@dataclass(frozen=True)
class ModelBundle:
    model: Any                           # Keras model
    label_encoder: Any                   # sklearn LabelEncoder
    preprocessing_config: PreprocessingConfig

@dataclass(frozen=True)
class PredictionResult:
    predicted_genre: str
    confidence: float
    probabilities: dict[str, float]

    def to_dict(self):
        return {
            "predicted_genre": self.predicted_genre,
            "confidence": self.confidence,
            "probabilities": self.probabilities
        }
```

**domain/interfaces.py - Protocols**

Define contratos (interfaces) que desacoplan la lógica de la implementación:

```python
class ModelRepository(Protocol):
    def load(self) -> ModelBundle:
        """Carga modelo + etiquetas + config."""

class AudioFeatureExtractor(Protocol):
    def extract(self, wav_bytes: bytes, config: PreprocessingConfig) -> np.ndarray:
        """Retorna features del audio."""

    def is_silent(self, wav_bytes: bytes) -> bool:
        """Detecta silencio."""

class GenrePredictionService(Protocol):
    def load(self) -> None:
    def is_ready(self) -> bool:
    def get_genres(self) -> list[str]:
    def predict(self, wav_bytes: bytes) -> PredictionResult:
```

Ventaja: podrías implementar estas interfaces con PyTorch en lugar de Keras sin cambiar el resto del código.

---

### `backend/app/services/genre_prediction_service.py` - Lógica de Negocio

```python
class KerasGenrePredictionService:
    def __init__(
        self,
        model_repository: ModelRepository,
        feature_extractor: AudioFeatureExtractor,
    ):
        self._model_repository = model_repository
        self._feature_extractor = feature_extractor
        self._bundle: ModelBundle | None = None

    def load(self) -> None:
        """Carga modelo en memoria."""
        self._bundle = self._model_repository.load()

    def is_ready(self) -> bool:
        """¿Modelo cargado?"""
        return self._bundle is not None

    def get_genres(self) -> list[str]:
        """Retorna los 10 géneros."""
        bundle = self._get_loaded_bundle()
        return bundle.label_encoder.classes_.tolist()

    def predict(self, wav_bytes: bytes) -> PredictionResult:
        """Pipeline completo de predicción."""
        bundle = self._get_loaded_bundle()

        # 1. Detectar silencio
        if self._feature_extractor.is_silent(wav_bytes):
            return PredictionResult(
                predicted_genre="no_music",
                confidence=1.0,
                probabilities={g: 0.0 for g in bundle.label_encoder.classes_}
                    | {"no_music": 1.0}
            )

        # 2. Extraer features (Mel Spectrogram)
        segments = self._feature_extractor.extract(
            wav_bytes,
            bundle.preprocessing_config
        )

        # 3. Predecir (modelo retorna probs de cada segmento)
        segment_probs = bundle.model.predict(segments)
        # shape: (N_segmentos, 10)

        # 4. Promediar probabilidades
        avg_probs = np.mean(segment_probs, axis=0)
        # shape: (10,)

        # 5. Género ganador
        pred_idx = int(np.argmax(avg_probs))
        pred_genre = bundle.label_encoder.inverse_transform([pred_idx])[0]
        confidence = float(avg_probs[pred_idx])

        # 6. Diccionario de probs
        probs_dict = {
            genre: float(avg_probs[i])
            for i, genre in enumerate(bundle.label_encoder.classes_)
        }

        return PredictionResult(
            predicted_genre=pred_genre,
            confidence=confidence,
            probabilities=probs_dict
        )
```

**¿Por qué promediar segmentos?**

- Un audio de 10 segundos = 2-3 segmentos de 4 segundos
- Modelo predice cada segmento por separado
- Promediar = decisión más robusta (basada en todo el audio, no una parte)

---

### `backend/app/infrastructure/keras_model_repository.py` - Carga Modelo

```python
class KerasModelRepository:
    def load(self) -> ModelBundle:
        self._ensure_artifacts_exist()

        # Importar custom layer
        from training.panns_cnn10 import AudioPooling

        # Cargar modelo
        model = keras.models.load_model(
            self._settings.model_path,
            custom_objects={"AudioPooling": AudioPooling},
            compile=False  # No recompilamos, solo prediction
        )

        # Cargar label encoder
        with open(self._settings.label_encoder_path, "rb") as f:
            label_encoder = pickle.load(f)

        # Cargar configuración de preprocesamiento
        preprocessing_config = self._load_preprocessing_config()

        return ModelBundle(model, label_encoder, preprocessing_config)
```

**Punto importante:** `custom_objects={"AudioPooling": AudioPooling}` es necesario porque AudioPooling es una capa custom que Keras no reconoce por defecto.

---

### `backend/app/infrastructure/audio_feature_extractor.py` - Extrae Features

```python
class TrainingAudioFeatureExtractor:
    def __init__(self, project_root: Path):
        training_path = project_root / "training"
        sys.path.append(str(training_path))

        from preprocessing import is_audio_silent, process_audio_file
        self._is_audio_silent = is_audio_silent
        self._process_audio_file = process_audio_file

    def extract(self, wav_bytes: bytes, config: PreprocessingConfig) -> np.ndarray:
        """
        Extrae Mel Spectrogram del audio.
        Reutiliza el código de entrenamiento para garantizar consistencia.
        """
        temp_path = None
        try:
            # Guardar bytes en archivo temporal
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
                f.write(wav_bytes)
                temp_path = f.name

            # Llamar función de entrenamiento
            return self._process_audio_file(
                temp_path,
                feature_type=config.feature_type,
                segment_duration=config.segment_duration,
                overlap=config.overlap,
            )
        finally:
            # Eliminar archivo temporal
            if temp_path:
                Path(temp_path).unlink(missing_ok=True)

    def is_silent(self, wav_bytes: bytes) -> bool:
        """Detecta audios silenciosos."""
        # Similar a extract pero usa is_audio_silent
```

**Ventaja clave:** Reutiliza el mismo código de preprocessing que en entrenamiento. Garantiza que el modelo recibe exactamente el mismo tipo de input que durante el entrenamiento.

---

## Flujo Completo de Predicción

```
Usuario sube archivo.wav en el frontend
     ↓
POST /predict con archivo
     ↓
FastAPI route /predict recibe request
     ↓
Lee archivo → wav_bytes
     ↓
Llama service.predict(wav_bytes)
     ↓
1. ¿Audio silencioso? → is_audio_silent(wav_bytes)
   Sí → return {predicted_genre: "no_music", confidence: 1.0}
   No → continuar
     ↓
2. Extraer features
   - Escribir bytes en archivo temporal
   - process_audio_file(temp_file)
     - Cargar audio con librosa
     - Dividir en segmentos de 4s
     - Cada segmento → Mel Spectrogram
     - Normalización
     - shape: (N_seg, 64, 128, 1)
     ↓
3. Predecir
   - model.predict(segmentos)
   - shape output: (N_seg, 10)
     ↓
4. Promediar
   - np.mean(probs, axis=0)
   - shape: (10,)
     ↓
5. Argmax + conversión de índice
   - idx = argmax(probs)
   - genre = label_encoder.inverse_transform([idx])
   - confidence = probs[idx]
     ↓
6. Crear diccionario de probabilidades
   - {"blues": 0.01, "classical": 0.00, ..., "rock": 0.91}
     ↓
7. Retornar PredictionResult
     ↓
FastAPI serializa a JSON
     ↓
Response HTTP 200 con JSON
```

---

## Requisitos

### Entrenamiento

```
tensorflow>=2.13.0
keras>=3.0.0
librosa>=0.10.0
numpy>=1.24.0
scikit-learn>=1.3.0
scipy>=1.10.0
matplotlib>=3.8.0
```

### Backend

```
fastapi>=0.104.0
uvicorn>=0.24.0
pydantic>=2.0.0
tensorflow>=2.13.0
keras>=3.0.0
librosa>=0.10.0
numpy>=1.24.0
scikit-learn>=1.3.0
```

---

## Ejecución

**1. Entrenar modelo (primera vez)**

```bash
cd training
python train.py --dataset_path ../dataset
# Genera:
# - models/best_model.keras
# - models/label_encoder.pkl
# - models/preprocessing_config.json
```

**2. Iniciar backend**

```bash
cd backend
uvicorn main:app --reload
# http://localhost:8000/docs  ← Swagger UI
# http://localhost:8000/redoc  ← ReDoc
```

**3. Probar API**

```bash
# Health check
curl http://localhost:8000/health

# Listar géneros
curl http://localhost:8000/genres

# Predecir
curl -X POST -F "file=@audio.wav" http://localhost:8000/predict
```

---

## Resumen de Componentes

| Archivo                                     | Líneas | Propósito                                   |
| ------------------------------------------- | ------ | ------------------------------------------- |
| `config.py`                                 | 50     | Parámetros centralizados                    |
| `preprocessing.py`                          | 300+   | Cargar audio → Mel Spectrogram              |
| `augmentations.py`                          | 100    | Data augmentation (ruido, pitch shift, etc) |
| `panns_cnn10.py`                            | 220    | Arquitectura CNN10 + carga de pesos         |
| `train.py`                                  | 450    | Orquestación de entrenamiento               |
| `routes.py`                                 | 60     | 4 endpoints HTTP                            |
| `services/genre_prediction_service.py`      | 79     | Lógica de predicción                        |
| `infrastructure/keras_model_repository.py`  | 58     | Carga modelo desde disco                    |
| `infrastructure/audio_feature_extractor.py` | 60     | Extrae features del audio                   |

**Total: ~1500 líneas de código funcional**

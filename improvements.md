# Craneshot Codebase Improvement Suggestions

This document outlines architectural, naming, and structural improvements for the Craneshot Camera Mod codebase.

## Core Architecture Improvements

### 1. Merge ICameraMovement and AbstractMovementSettings

**Current Issue:** The relationship between `ICameraMovement` interface and `AbstractMovementSettings` abstract class creates confusion. `AbstractMovementSettings` doesn't implement `ICameraMovement`, yet concrete movement implementations extend `AbstractMovementSettings` and implement `ICameraMovement`.

**Recommendation:** 
- Refactor to make `AbstractMovementSettings` implement `ICameraMovement`
- Rename `AbstractMovementSettings` to `AbstractCameraMovement` for clarity
- Move all common functionality from concrete implementations into the abstract class

```java
public abstract class AbstractCameraMovement implements ICameraMovement {
    // Existing AbstractMovementSettings fields
    // Default implementations of ICameraMovement methods
}
```

This would streamline inheritance and prevent duplication of code across camera movement implementations.

### 2. Clearer Separation of Camera Modes and States

**Current Issue:** Camera operations are scattered across multiple classes (`CameraController`, `CameraMovementManager`, `CameraSystem`), creating unclear responsibilities and potential for state inconsistencies.

**Recommendation:**
- Create a clear `CameraState` model class that encapsulates all camera state
- Implement a proper state machine for camera modes with well-defined transitions
- Centralize state access in `CameraSystem` and have other components request state changes through it

### 3. Simplify Post-Movement Controls Architecture

**Current Issue:** Post-movement controls are managed through enum constants and multiple maps in `CameraController` and `CameraMovementManager`, making the control flow difficult to follow.

**Recommendation:**
- Create a dedicated `PostMovementController` class
- Implement a Strategy pattern for different post-movement behaviors
- Replace enum constants with proper polymorphic classes

## Class-specific Improvements

### CameraSystem

1. **Rename Methods for Clarity**:
   - `updateCamera` → `applyCameraPositionAndRotation`
   - `handleMovementInput` → `processKeyboardMovementInput`
   - `updateRotation` → `processMouseLookInput`

2. **Add Clear Camera Mode Transitions**:
   - Create explicit methods like `transitionToFreeCameraMode()`, `transitionToFollowMode()`, etc.
   - Implement proper lifecycle hooks (onEnter, onExit) for each camera mode

### CameraController

1. **Method Renaming**:
   - `updateCamera` → `updateCameraPosition`
   - `handleCameraUpdate` → `syncCameraWithMovement`
   - `updatePerspective` → `adjustPerspectiveBasedOnDistance`

2. **Responsibility Redistribution**:
   - Move message handling to a dedicated `UserInterfaceController` class
   - Move camera rotation logic to `CameraSystem`

### CameraMovementManager

1. **Method Improvements**:
   - `calculateState` → `computeMovementState` (more descriptive)
   - `startTransition` → `beginCameraMovement`
   - `finishTransition` → `completeCameraMovement`

2. **Structural Improvements**:
   - Split into smaller, focused classes: `SlotManager`, `MovementTransitionManager`, `MovementStateCalculator`
   - Implement the Command pattern for more declarative movement transitions

### Camera Movements

1. **Standardize Movement Classes**:
   - Use consistent field names and method signatures across all movement implementations
   - Create stronger base functionality in the abstract class
   - Consider using composition over inheritance for specialized behaviors

2. **Naming Consistency**:
   - Rename methods to follow verb-noun pattern (e.g., `calculateState` → `computeMovementState`)
   - Rename fields for better self-documentation (e.g., `alpha` → `movementProgress`)

## System-wide Improvements

### 1. Consistent Error Handling

**Current Issue:** Error handling is inconsistent, mixing log messages with silent failures.

**Recommendation:**
- Implement a consistent error handling strategy
- Create meaningful exception classes
- Centralize logging for easier debugging

### 2. Configuration System Improvements

**Current Issue:** Settings are scattered across multiple classes and use a mix of approaches.

**Recommendation:**
- Consolidate settings into a hierarchical structure
- Implement a proper observer pattern for settings changes
- Improve serialization/deserialization with proper validation

### 3. Consistent Event System

**Current Issue:** The code mixes direct method calls with event-based approaches.

**Recommendation:**
- Implement a proper event bus for camera-related events
- Define clear event types and handlers
- Reduce direct coupling between components

### 4. Documentation Improvements

**Current Issue:** Documentation is sparse and inconsistent.

**Recommendation:**
- Add comprehensive class-level JavaDoc
- Document all public methods
- Include usage examples in documentation
- Create architecture diagrams

## Specific Refactoring Tasks

1. **Movement Calculation Refactoring**
   - Extract complex mathematical operations into utility classes
   - Implement vector operation utilities for readability
   - Consider using a proper math library

2. **User Interface Separation**
   - Extract UI-related code from movement logic
   - Implement a proper MVC pattern for settings screens
   - Make UI components more reusable

3. **Raycast Improvements**
   - Refactor `RaycastUtil` to use more descriptive method names
   - Implement more sophisticated collision detection
   - Add visualization helpers for debugging

4. **Mixin Organization**
   - Group mixins by functionality rather than target class
   - Improve documentation of mixin purposes
   - Consider reducing mixin count by using more targeted approaches

## Class Renaming and Reorganization

1. **Package Structure**
   - Reorganize packages by feature rather than technical layer
   - Create separate packages for movement types, settings, UI, and core functionality

2. **Class Renaming**
   - `FreeCamReturnMovement` → `ReturnToPlayerMovement`
   - `OrthographicCameraManager` → `OrthographicModeController`
   - `MouseInterceptor` → `MouseInputController`

3. **Interface Refinement**
   - Create more focused interfaces for specific behaviors
   - Follow interface segregation principle

## Implementation Priority

1. Merge `ICameraMovement` and `AbstractMovementSettings`
2. Centralize camera state management in `CameraSystem`
3. Implement consistent event system
4. Refactor movement calculation logic
5. Reorganize packages and rename classes
6. Improve documentation

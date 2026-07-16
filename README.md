# slot-game-controller-service

Spin orchestration service for the `slot-central` microservices platform.

Pure orchestration: validate player session (Auth) → reserve bet (Bank) → compute spin result (Game Engine) → settle (Bank) → return result, persisting game history.

This repository is part of a re-architecture of the `slot-central-server-express-rmq` Node.js EGM slot-floor backend into Spring Boot microservices.

Scaffolding in progress.

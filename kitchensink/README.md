# 🥘 Kitchen Sink Application

A reference **Spring Boot 3.x** application showcasing a member management system with both **Admin** and **Member** flows.  
This project demonstrates **application modernization** (modular architecture, Spring Security, MongoDB, etc.) and serves as a base for future extensibility (e.g., microservices, async jobs, Kafka/RabbitMQ integration).

---

## ✨ Features

### Admin
- Secure **login** with role-based access control.
- **Register new members** (only admins can add).
- **Search, view, update, and delete** member details.
- **Pagination & sorting** available in listings.
- **Export reports** in CSV/Excel.
- Members are **notified by email** when their details are updated or deleted.
- **Approval workflow**: admin approves/rejects member self-updates via email notification.

### Member
- Secure **login** with username/password.
- **Self-service profile management**:
    - View own details.
    - Submit profile updates (pending admin approval).
- Receive **email notifications** on status of update requests.

### Notifications
- Email integration (e.g., Mailpit locally, SMTP/SES in production).
- Admins notified on pending requests.
- Members notified when:
    - Added with a temporary password (must reset on first login).
    - Their details are updated/deleted.
    - Admin approves/rejects their change requests.

### Security
- **Spring Security** with:
    - **Form login** for UI (Thymeleaf pages).
    - Role-based access (`ADMIN`, `MEMBER`).
    - Custom login success handler (role-based redirects, reset-password flow).
- Passwords hashed with **BCrypt**.
- Configurable **session vs. JWT** separation:
    - UI uses **session cookies**.
    - APIs can be secured with **JWT** for external clients.

---

## 🛠️ Technologies Used

- **Java 21**
- **Spring Boot 3.5.x**
    - Spring Web (REST + Thymeleaf MVC)
    - Spring Security
    - Spring Data (MongoDB)
- **MongoDB** for persistence
- **Thymeleaf** for server-rendered pages
- **Lombok** for boilerplate reduction
- **JUnit + Mockito** for testing
- **Maven (multi-module build)** for modular architecture
- **Mailpit / SMTP** for email notifications
- **Docker (optional)** for local setup

---

## 📂 Project Structure


- `api` depends only on **interfaces** in `core` (ports).
- `core` has **implementations** (adapters).
- `main` wires everything up (config, security, entrypoint).

---

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+
- MongoDB running (local or container)

### Run locally
```bash
# Clone
git clone https://github.com/<your-username>/kitchen-sink.git
cd kitchen-sink

# Build
mvn clean install

# Run
cd main
mvn spring-boot:run

App starts at: http://localhost:8080
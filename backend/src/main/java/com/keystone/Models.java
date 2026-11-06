package com.keystone;

import jakarta.persistence.*;
import java.time.Instant;

enum Role { ADMIN, DISPATCHER, TECHNICIAN, CUSTOMER }
enum Priority { LOW(72), MEDIUM(24), HIGH(8), CRITICAL(4);
  final int slaHours; Priority(int h) { slaHours = h; } }
enum Status { OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD, COMPLETED, CLOSED;
  boolean canMoveTo(Status n) {
    return switch (this) {
      case OPEN -> n == ASSIGNED;
      case ASSIGNED -> n == IN_PROGRESS || n == OPEN;
      case IN_PROGRESS -> n == ON_HOLD || n == COMPLETED;
      case ON_HOLD -> n == IN_PROGRESS;
      case COMPLETED -> n == CLOSED || n == IN_PROGRESS;
      case CLOSED -> false;
    };
  }
}

@Entity @Table(name = "users")
class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(unique = true, nullable = false) public String username;
  @com.fasterxml.jackson.annotation.JsonIgnore public String password;
  public String fullName;
  @Enumerated(EnumType.STRING) public Role role;
  protected User() {}
  User(String u, String p, String n, Role r) { username = u; password = p; fullName = n; role = r; }
}

@Entity
class WorkOrder {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String title, facility, customer, technician;
  @Column(length = 2000) public String description;
  @Enumerated(EnumType.STRING) public Priority priority = Priority.MEDIUM;
  @Enumerated(EnumType.STRING) public Status status = Status.OPEN;
  public Instant createdAt = Instant.now(), slaDueAt, completedAt;
  boolean breached() {
    Instant end = completedAt != null ? completedAt : Instant.now();
    return slaDueAt != null && end.isAfter(slaDueAt);
  }
  @com.fasterxml.jackson.annotation.JsonProperty("slaBreached") public boolean isSlaBreached() { return breached(); }
}

@Entity class TimeEntry {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public Long workOrderId; public String technician, note; public int minutes;
  public Instant loggedAt = Instant.now();
}

@Entity class PartUsage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public Long workOrderId; public String partName; public int quantity; public double unitCost;
}

interface UserRepo extends org.springframework.data.jpa.repository.JpaRepository<User, Long> {
  java.util.Optional<User> findByUsername(String u);
}
interface WorkOrderRepo extends org.springframework.data.jpa.repository.JpaRepository<WorkOrder, Long> {
  java.util.List<WorkOrder> findByCustomerOrderByCreatedAtDesc(String c);
  java.util.List<WorkOrder> findByTechnicianOrderByCreatedAtDesc(String t);
  java.util.List<WorkOrder> findAllByOrderByCreatedAtDesc();
}
interface TimeRepo extends org.springframework.data.jpa.repository.JpaRepository<TimeEntry, Long> {
  java.util.List<TimeEntry> findByWorkOrderId(Long id);
}
interface PartRepo extends org.springframework.data.jpa.repository.JpaRepository<PartUsage, Long> {
  java.util.List<PartUsage> findByWorkOrderId(Long id);
}

package com.keystone;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record LoginReq(@NotBlank String username, @NotBlank String password) {}
record RegisterReq(@NotBlank String username, @Size(min = 6) String password, @NotBlank String fullName) {}
record CreateReq(@NotBlank String title, String description, String facility, Priority priority) {}
record AssignReq(@NotBlank String technician) {}
record StatusReq(Status status) {}
record TimeReq(@Min(1) int minutes, String note) {}
record PartReq(@NotBlank String partName, @Min(1) int quantity, @PositiveOrZero double unitCost) {}

@RestController @RequestMapping("/api/auth")
class AuthController {
  private final UserRepo users; private final PasswordEncoder pe; private final JwtService jwt;
  AuthController(UserRepo u, PasswordEncoder p, JwtService j) { users = u; pe = p; jwt = j; }

  @PostMapping("/login") Map<String, Object> login(@RequestBody @jakarta.validation.Valid LoginReq r) {
    User u = users.findByUsername(r.username()).filter(x -> pe.matches(r.password(), x.password))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
    return Map.of("token", jwt.create(u), "role", u.role, "fullName", u.fullName, "username", u.username);
  }

  /** Customer self-service sign-up. Staff accounts are created by admins only. */
  @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> register(@RequestBody @jakarta.validation.Valid RegisterReq r) {
    if (users.findByUsername(r.username()).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
    User u = users.save(new User(r.username(), pe.encode(r.password()), r.fullName(), Role.CUSTOMER));
    return Map.of("token", jwt.create(u), "role", u.role, "fullName", u.fullName, "username", u.username);
  }
}

@RestController @RequestMapping("/api")
class WorkOrderController {
  private final WorkOrderRepo orders; private final TimeRepo times; private final PartRepo parts; private final UserRepo users;
  WorkOrderController(WorkOrderRepo o, TimeRepo t, PartRepo p, UserRepo u) { orders = o; times = t; parts = p; users = u; }

  private Role roleOf(Authentication a) { return Role.valueOf(a.getAuthorities().iterator().next().getAuthority().substring(5)); }
  private WorkOrder find(Long id) {
    return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found"));
  }
  /** Customers see their own orders, technicians their assigned ones, staff see everything. */
  private void checkAccess(WorkOrder w, Authentication a) {
    Role r = roleOf(a);
    boolean ok = r == Role.ADMIN || r == Role.DISPATCHER
        || (r == Role.CUSTOMER && a.getName().equals(w.customer))
        || (r == Role.TECHNICIAN && a.getName().equals(w.technician));
    if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your work order");
  }

  @GetMapping("/work-orders") List<WorkOrder> list(Authentication a) {
    return switch (roleOf(a)) {
      case CUSTOMER -> orders.findByCustomerOrderByCreatedAtDesc(a.getName());
      case TECHNICIAN -> orders.findByTechnicianOrderByCreatedAtDesc(a.getName());
      default -> orders.findAllByOrderByCreatedAtDesc();
    };
  }

  @PostMapping("/work-orders") @ResponseStatus(HttpStatus.CREATED)
  WorkOrder create(@RequestBody @jakarta.validation.Valid CreateReq r, Authentication a) {
    if (roleOf(a) == Role.TECHNICIAN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Technicians cannot create orders");
    WorkOrder w = new WorkOrder();
    w.title = r.title(); w.description = r.description(); w.facility = r.facility();
    if (r.priority() != null) w.priority = r.priority();
    w.customer = a.getName();
    w.slaDueAt = w.createdAt.plus(w.priority.slaHours, ChronoUnit.HOURS);
    return orders.save(w);
  }

  @GetMapping("/work-orders/{id}") Map<String, Object> detail(@PathVariable Long id, Authentication a) {
    WorkOrder w = find(id); checkAccess(w, a);
    List<PartUsage> p = parts.findByWorkOrderId(id);
    return Map.of("order", w, "time", times.findByWorkOrderId(id), "parts", p,
        "partsCost", p.stream().mapToDouble(x -> x.quantity * x.unitCost).sum());
  }

  @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
  @PostMapping("/work-orders/{id}/assign") WorkOrder assign(@PathVariable Long id, @RequestBody @jakarta.validation.Valid AssignReq r) {
    User t = users.findByUsername(r.technician()).filter(u -> u.role == Role.TECHNICIAN)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown technician"));
    WorkOrder w = find(id);
    if (!w.status.canMoveTo(Status.ASSIGNED) && w.status != Status.ASSIGNED)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot assign an order that is " + w.status);
    w.technician = t.username; w.status = Status.ASSIGNED;
    return orders.save(w);
  }

  @PostMapping("/work-orders/{id}/status") WorkOrder status(@PathVariable Long id, @RequestBody StatusReq r, Authentication a) {
    WorkOrder w = find(id); checkAccess(w, a);
    Role role = roleOf(a);
    if (role == Role.CUSTOMER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customers cannot change status");
    if (r.status() == Status.CLOSED && role == Role.TECHNICIAN)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only dispatchers can close an order");
    if (!w.status.canMoveTo(r.status()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, w.status + " -> " + r.status() + " is not allowed");
    w.status = r.status();
    if (w.status == Status.COMPLETED) w.completedAt = Instant.now();
    return orders.save(w);
  }

  @PostMapping("/work-orders/{id}/time") @ResponseStatus(HttpStatus.CREATED)
  TimeEntry logTime(@PathVariable Long id, @RequestBody @jakarta.validation.Valid TimeReq r, Authentication a) {
    WorkOrder w = find(id); checkAccess(w, a);
    if (roleOf(a) == Role.CUSTOMER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff only");
    TimeEntry t = new TimeEntry(); t.workOrderId = id; t.technician = a.getName(); t.minutes = r.minutes(); t.note = r.note();
    return times.save(t);
  }

  @PostMapping("/work-orders/{id}/parts") @ResponseStatus(HttpStatus.CREATED)
  PartUsage addPart(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PartReq r, Authentication a) {
    WorkOrder w = find(id); checkAccess(w, a);
    if (roleOf(a) == Role.CUSTOMER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff only");
    PartUsage p = new PartUsage(); p.workOrderId = id; p.partName = r.partName(); p.quantity = r.quantity(); p.unitCost = r.unitCost();
    return parts.save(p);
  }

  @GetMapping("/technicians") @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
  List<User> technicians() { return users.findAll().stream().filter(u -> u.role == Role.TECHNICIAN).toList(); }

  @GetMapping("/dashboard") @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
  Map<String, Object> dashboard() {
    List<WorkOrder> all = orders.findAll();
    Map<Status, Long> byStatus = all.stream().collect(Collectors.groupingBy(w -> w.status, Collectors.counting()));
    long breached = all.stream().filter(WorkOrder::breached).count();
    long done = all.stream().filter(w -> w.completedAt != null).count();
    long onTime = all.stream().filter(w -> w.completedAt != null && !w.breached()).count();
    return Map.of("total", all.size(), "byStatus", byStatus, "slaBreached", breached,
        "slaCompliancePct", done == 0 ? 100 : Math.round(100.0 * onTime / done));
  }
}

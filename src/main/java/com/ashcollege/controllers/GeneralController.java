package com.ashcollege.controllers;

import com.ashcollege.entities.*;
import com.ashcollege.responses.ValidationRespons;
import com.ashcollege.responses.*;
import com.ashcollege.service.Persist;
import com.ashcollege.utils.ApiUtils;
import com.ashcollege.utils.Constants;
import com.ashcollege.utils.GeneralUtils;
import com.mysql.cj.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "http://localhost:3000")

public class    GeneralController {
    private HashMap<String,UserEntity> tempUsers = new HashMap<>();
    @Autowired
    private Persist persist;

    @RequestMapping("/get-username-by-token")
    public String getNameByToken(String token){
        String name = null;
        UserEntity user = this.persist.getUserByPass(token);
        if (user!=null){
            name = user.getUsername();
        }
        return name;
    }

    @RequestMapping("/get-permission")
    public int getPermission(String token){
        UserEntity user = this.persist.getUserByPass(token);
        return user.getRole().getId();
    }

    @RequestMapping("/check-recovery-password")
    public BasicResponse checkRecoveryPassword(String email,String pass_recovery) {
        boolean isValidPass = false;
        int errorCode = Constants.FAIL;
        UserEntity user = this.persist.getUserByEmailAndPasswordRecovery(email, pass_recovery);
        if (user != null) {
            user.setPasswordRecovery("");
            isValidPass = true;
            errorCode = Constants.SUCCESS;
        }
        return new BasicResponse(isValidPass, errorCode);

    }

    @RequestMapping("/check-otp-to-register")
    public RegisterResponse addUser(String username,String otp) {
        boolean registeredSuccessfully = true;
        int errorCode = Constants.SUCCESS;
        System.out.println(username+"1");
        UserEntity user = this.tempUsers.get(username);
        if (user==null||!user.getOtp().equals(otp)) {
            registeredSuccessfully = false;
            errorCode = Constants.FAIL;
            System.out.println(username+"2");

        }
        if (user!=null&&user.getOtp().equals(otp)){
            user.setOtp("");
            this.tempUsers.remove(username);
            System.out.println("lll"+user);
            this.persist.save(user);
            if (user.getRole().getId()==2){
                AdminEntity admin = new AdminEntity(user.getFullName(),user);
                this.persist.save(admin);
            }
            System.out.println(user);
        }
        return new RegisterResponse(true, errorCode, registeredSuccessfully);
    }
    // send a temporary password via SMS
    @RequestMapping("/forgot-password")
    public BasicResponse forgotPassword(@RequestParam String username) {
        BasicResponse resp = new BasicResponse();
        UserEntity user = persist.getUserByUsername(username);
        if (user != null) {
            // generate a temporary code (e.g. 6‑digit OTP)
            String temp = GeneralUtils.generateOtp();
            user.setPasswordRecovery(temp);
            persist.save(user);
            // send via SMS
            ApiUtils.sendSms(temp, List.of(user.getPhoneNumber()));
            resp.setSuccess(true);
            resp.setErrorCode(Constants.SUCCESS);
        } else {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.USER_NOT_EXIST);
        }
        return resp;
    }

    // אימות הקוד הזמני בלבד – לא מוחק ולא משנה סיסמה
    @PostMapping("/verify-recovery-code")
    public BasicResponse verifyRecoveryCode(@RequestParam String username, @RequestParam String recovery) {
        BasicResponse resp = new BasicResponse(false, Constants.FAIL);
        UserEntity user = persist.getUserByUsername(username);
        if (user != null && user.isActive() && recovery.equals(user.getPasswordRecovery())) {
            resp.setSuccess(true);
            resp.setErrorCode(Constants.SUCCESS);
        }
        return resp;
    }
    // איפוס הסיסמה (מניח שהקוד כבר אומת בשלב הקודם)
    @PostMapping("/reset-password")
    public BasicResponse resetPassword(@RequestParam String username, @RequestParam("newPassword") String newPass) {
        BasicResponse resp = new BasicResponse(false, Constants.FAIL);
        UserEntity user = persist.getUserByUsername(username);
        if (user != null && user.isActive()) {
            String hashed = GeneralUtils.hashMd5(username, newPass);
            user.setPassword(hashed);
            user.setPasswordRecovery("");
            persist.save(user);
            resp.setSuccess(true);
            resp.setErrorCode(Constants.SUCCESS);
        }
        return resp;
    }


    @PostMapping("/register")
    @Transactional
    public ValidationRespons register(HttpServletRequest request) {
        ValidationRespons response = new ValidationRespons();

        try {
            // קריאת פרמטרים מהבקשה
            String userName = request.getParameter("userName");
            String password = request.getParameter("password");
            String firstName = request.getParameter("name");
            String lastName = request.getParameter("lastName");
            String email = request.getParameter("email");
            String phoneNumber = request.getParameter("phoneNumber");
            int teamId = Integer.parseInt(request.getParameter("teamId"));
            String abilitiesStr = request.getParameter("abilities"); // "Java,Python"

            // בדיקה אם שם המשתמש כבר קיים
            if (persist.getUserByUsername(userName) != null) {
                response.setSuccess(false);
                response.setMessage("שם המשתמש כבר קיים");
                return response;
            }

            // יצירת המשתמש
            String hashedPassword = GeneralUtils.hashMd5(userName, password);
            UserEntity user = new UserEntity(userName, hashedPassword, firstName, lastName, email,"worker", phoneNumber);
            user.setTeamId(teamId);

            RoleEntity role = new RoleEntity();
            role.setId(1); // בהנחה ש־1 זה worker
            user.setRole(role);

            // שמירה של המשתמש והיכולות
            persist.save(user);
            persist.save(new UserAbilityEntity(user.getId(), abilitiesStr));

            response.setSuccess(true);
            response.setMessage("העובד נוסף בהצלחה");
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("שגיאה במהלך הרישום");
            return response;
        }
    }
    private boolean isValidPhoneNumber(String phoneNumber) {
        if(GeneralUtils.isValidPhoneNumber(phoneNumber)){
            List<UserEntity> users = persist.loadList(UserEntity.class);
            System.out.println(users);
            List<UserEntity> temp = users.stream().filter(user ->user.getPhoneNumber()!=null&&user.getPhoneNumber().equals(phoneNumber)).toList();
            return temp.isEmpty();
        }
        return false;
    }

    // /login – כבר אחרי שמצאנו משתמש ב-getUserByUsernameAndPass הוא פעיל בלבד
// אין שינוי לוגי נוסף נדרש כאן.

    // /check-otp – להוסיף בדיקת isActive
    @RequestMapping(value = "/check-otp", method = {RequestMethod.GET, RequestMethod.POST})
    public LoginResponse checkOtp(String username, String password, String otp) {
        LoginResponse response = new LoginResponse();
        String hash = GeneralUtils.hashMd5(username , password);
        UserEntity user = persist.getUserByUsernameAndPass(username, hash);
        if (user != null && user.isActive()) {
            if (otp != null && otp.equals(user.getOtp())) {
                response.setSuccess(true);
                user.setOtp("");
                response.setPermission(user.getRole().getId());
                response.setToken(hash);
                response.setId(user.getId());
                response.setErrorCode(Constants.SUCCESS);
                persist.save(user);
            }
        }
        return response;
    }

    @GetMapping("/check-username")
    @ResponseBody
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        boolean taken = persist.getUserByUsername(username) != null;
        return Map.of("taken", taken);
    }
    @GetMapping("/all-users")
    @ResponseBody
    public List<Map<String, String>> getAllUsers() {
        return persist.loadActiveUsers().stream()
                .map(user -> Map.of("username", user.getUsername()))
                .collect(Collectors.toList());
    }

    @GetMapping("/eligible-workers")
    @ResponseBody
    public List<Map<String, String>> getEligibleWorkers() {
        return persist.getEligibleWorkers();
    }


    @PostMapping("/create-team")
    @ResponseBody
    @Transactional
    public ValidationRespons createTeam(@RequestParam String name,
                                        @RequestParam String leaderUsername,
                                        @RequestParam List<String> memberUsernames) {
        ValidationRespons response = new ValidationRespons();
        try {
            UserEntity leader = persist.getUserByUsername(leaderUsername);
            if (leader == null) {
                response.setSuccess(false);
                response.setMessage("ראש הצוות לא נמצא");
                return response;
            }

            int oldRoleId = leader.getRole().getId();
            if (oldRoleId != 1 && oldRoleId != 3) {
                response.setSuccess(false);
                response.setMessage("ראש הצוות חייב להיות עובד (1) או מנהל (3)");
                return response;
            }

            // 1. יצירת הצוות
            TeamEntity team = new TeamEntity();
            team.setName(name);
            team.setHead(leader);
            persist.save(team);
            int teamId = team.getId();

            // 2. קביעת התפקיד החדש לפי התפקיד הישן
            RoleEntity newRole = new RoleEntity();
            if (oldRoleId == 1) {
                newRole.setId(2);  // עובד רגיל (1) → ראש צוות (2)
            } else {
                newRole.setId(4);  // מנהל (3) → מנהל-על (4)
            }
            leader.setRole(newRole);
            leader.setTeamId(teamId);
            persist.save(leader);

            // 3. עדכון חברים שיירשמו לצוות
            for (String username : memberUsernames) {
                UserEntity user = persist.getUserByUsername(username);
                if (user != null && user.getRole().getId() == 1) {
                    // רק עובד רגיל יכול להצטרף כחבר
                    user.setTeamId(teamId);
                    persist.save(user);
                }
            }

            response.setSuccess(true);
            response.setMessage("צוות נוצר בהצלחה");
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("שגיאה בעת יצירת הצוות");
            return response;
        }
    }


    @PostMapping("/add-client")
    @ResponseBody
    @Transactional
    public ValidationRespons addClient(@RequestParam String name, @RequestParam String managerUsername) {
        ValidationRespons response = new ValidationRespons();
        try {
            UserEntity manager = persist.getUserByUsername(managerUsername);
            if (manager == null) {
                response.setSuccess(false);
                response.setMessage("מנהל לא קיים");
                return response;
            }

            ClientEntity client = new ClientEntity();
            client.setName(name);
            client.setManager(manager);
            client.setStatus("1");
            persist.save(client);

            response.setSuccess(true);
            response.setMessage("הלקוח נוסף בהצלחה");
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.setSuccess(false);
            response.setMessage("שגיאה בעת הוספת לקוח");
            return response;
        }
    }

    @RequestMapping(value = "/login", method = {RequestMethod.GET, RequestMethod.POST})
    public BasicResponse login(String username, String password){
        System.out.println("kk" + username);
        BasicResponse response = new BasicResponse();
        String hash = GeneralUtils.hashMd5(username, password);
        System.out.println(hash);
        UserEntity user = persist.getUserByUsernameAndPass(username, hash);

        if (user != null){
            String otp = GeneralUtils.generateOtp();
            user.setOtp(otp);
            persist.save(user);

            // הודעה רשמית בעברית
            String msg = "שלום " + user.getFullName() + ",\nקוד האימות שלך לאתר הוא: " + otp + "\nנא לא לשתף קוד זה.";
            ApiUtils.sendSms(msg, List.of(user.getPhoneNumber()));

            response.setSuccess(true);
            response.setErrorCode(Constants.SUCCESS);
        } else {
            response.setErrorCode(Constants.USER_NOT_EXIST);
        }

        return response;
    }


    @RequestMapping("/update-password")
    public BasicResponse updatePassword(String username, String password) {
        BasicResponse resp = new BasicResponse(false, Constants.FAIL);
        UserEntity user = persist.getUserByUsername(username);
        if (user != null && user.isActive()) {
            user.setPassword(password);
            persist.save(user);
            resp.setSuccess(true);
            resp.setErrorCode(Constants.SUCCESS);
        }
        return resp;
    }


    public boolean isUsernameExists(String username) {
        List<UserEntity> users = persist.loadList(UserEntity.class);
        List<UserEntity> temp = users.stream().filter(user -> user.getUsername().equals(username)).toList();
        return temp.isEmpty();
    }
    public boolean isEmailExists( String email) {
        List<UserEntity> users = persist.loadList(UserEntity.class);
        List<UserEntity> temp = users.stream().filter(user -> user.getEmail().equals(email)).toList();
        return temp.isEmpty();
    }
    @GetMapping("/all-teams")
    @ResponseBody
    public List<Map<String, Object>> getAllTeams() {
        return persist.loadList(TeamEntity.class).stream()
                .map(t -> {
                    Map<String,Object> m = new HashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    return m;
                })
                .collect(Collectors.toList());
    }
    private UserEntity userByToken(String token) {
        if (token == null) return null;
        return persist.getUserByPass(token);
    }

    private boolean isAdmin(UserEntity u) {
        return u.getRole().getId() == 3;
    }

    private boolean isTeamLeader(UserEntity u) {
        return u.getRole().getId() == 2;
    }

    @GetMapping("/pending")
    public Object getPendingTasks(@RequestParam String token) {
        UserEntity me = userByToken(token);
        if (me == null) {
            BasicResponse err = new BasicResponse();
            err.setSuccess(false);
            err.setErrorCode(-1);
            err.setMessage("Invalid token");
            return err;
        }

        List<TaskAssignmentEntity> all = persist.loadList(TaskAssignmentEntity.class);
        // סנן רק משימות במצב "assigned"
        List<TaskAssignmentEntity> assigned = all.stream()
                .filter(t -> "assigned".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());

        List<TaskAssignmentEntity> visible;
        if (isAdmin(me)) {
            visible = assigned;
        } else if (isTeamLeader(me)) {
            int myTeam = me.getTeamId();
            visible = assigned.stream()
                    .filter(t ->
                            // או הוקצה ישירות לי:
                            t.getUser().getId() == me.getId()
                                    // או הוקצה לחבר צוות שלי:
                                    || t.getUser().getTeamId() == myTeam
                    )
                    .collect(Collectors.toList());
        } else {
            // עובד רגיל
            visible = assigned.stream()
                    .filter(t -> t.getUser().getId() == me.getId())
                    .collect(Collectors.toList());
        }

        // ממיר ל־DTO פשוט של JSON
        return visible.stream().map(t -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("taskName", t.getTask().getName());
            m.put("client", t.getClient().getName());
            m.put("assignedTo", t.getUser().getUsername());
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/completed")
    public Object getCompletedTasks(@RequestParam String token) {
        UserEntity me = userByToken(token);
        if (me == null) {
            BasicResponse err = new BasicResponse();
            err.setSuccess(false);
            err.setErrorCode(-1);
            err.setMessage("Invalid token");
            return err;
        }

        List<TaskAssignmentEntity> all = persist.loadList(TaskAssignmentEntity.class);
        // סנן רק משימות במצב "completed"
        List<TaskAssignmentEntity> done = all.stream()
                .filter(t -> "completed".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());

        List<TaskAssignmentEntity> visible;
        if (isAdmin(me)) {
            visible = done;
        } else if (isTeamLeader(me)) {
            int myTeam = me.getTeamId();
            visible = done.stream()
                    .filter(t ->
                            t.getUser().getId() == me.getId()
                                    || t.getUser().getTeamId() == myTeam
                    )
                    .collect(Collectors.toList());
        } else {
            visible = done.stream()
                    .filter(t -> t.getUser().getId() == me.getId())
                    .collect(Collectors.toList());
        }

        return visible.stream().map(t -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("taskName", t.getTask().getName());
            m.put("client", t.getClient().getName());
            m.put("assignedTo", t.getUser().getUsername());
            return m;
        }).collect(Collectors.toList());
    }
    @GetMapping("/abilities")
    public List<AbilityEntity> fetchAllAbilities() {
        return persist.getAllAbilities();
    }
    @PostMapping("/tasks")
    public ResponseEntity<BasicResponse> createTask(@RequestBody TaskRequest req) {
        try {
            TaskEntity saved = persist.saveTaskWithRequirements(req);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new BasicResponse(true, 0));  // 0 = SUCCESS
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BasicResponse(false, 1));  // 1 = FAIL
        }
    }

    @PostMapping("/abilities")
    public ResponseEntity<AbilityEntity> createAbility(@RequestBody Map<String,String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AbilityEntity a = new AbilityEntity();
        a.setName(name);
        AbilityEntity saved = persist.saveAbility(a);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    @PostMapping("/deleteAbility")
    public ResponseEntity<Void> deleteAbility(@RequestParam("name") String name) {
        try {
            name = java.net.URLDecoder.decode(name, "UTF-8"); // הוספת פענוח
            persist.deleteAbilityByName(name);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            System.err.println("Error deleting ability: " + e.getMessage()+name);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/tasks")
    public List<TaskResponse> getTasks() {
        return persist.getAllTasksWithRequirements();
    }

    @GetMapping("/clients")
    public List<ClientResponse> getClients() {
        return persist.getAllClients();
    }

    @GetMapping("/workers")
    public List<WorkerDto> getWorkers() {
        return persist.getAllWorkersWithData();
    }

    @GetMapping("/find-workers")
    public List<WorkerDto> findWorkers(
            @RequestParam int taskId,
            @RequestParam int clientId
    ) {
        // 1) load requirements
        List<String> reqs = persist.getTaskRequirements(taskId);
        // 2) load all workers
        List<WorkerDto> all = persist.getAllWorkersWithData();
        // 3) load the client DTO (with managerUsername & managerTeamId)
        ClientResponse client = persist.getAllClients()
                .stream()
                .filter(c -> c.getId() == clientId)
                .findFirst()
                .orElseThrow();

        String mgrU   = client.getManagerUsername();
        int    mgrT   = client.getManagerTeamId();

        // split lists
        var same  = all.stream().filter(w->w.getTeamId()==mgrT).toList();
        var other = all.stream().filter(w->w.getTeamId()!=mgrT).toList();

        var byHours = Comparator.comparingInt(WorkerDto::getHoursWorked);

        List<WorkerDto> out = new ArrayList<>();

        // 4) manager first if capable
        same.stream()
                .filter(w->w.getUsername().equals(mgrU) && w.getAbilities().containsAll(reqs))
                .findFirst().ifPresent(out::add);

        // 5) same-team capable
        same.stream()
                .filter(w->!w.getUsername().equals(mgrU) && w.getAbilities().containsAll(reqs))
                .sorted(byHours).forEach(out::add);

        // 6) same-team not capable
        same.stream()
                .filter(w->!w.getAbilities().containsAll(reqs))
                .sorted(byHours).forEach(out::add);

        // 7) other-team capable
        other.stream()
                .filter(w->w.getAbilities().containsAll(reqs))
                .sorted(byHours).forEach(out::add);

        // 8) other-team not capable
        other.stream()
                .filter(w->!w.getAbilities().containsAll(reqs))
                .sorted(byHours).forEach(out::add);

        return out;
    }
    // GeneralController.java
    @PostMapping("/tasks/assign")
    public BasicResponse assignTask(
            @RequestParam String token,
            @RequestParam int taskId,
            @RequestParam int clientId,
            @RequestParam("usernames") List<String> usernames,
            @RequestParam int hours         // <-- השעה שהוזנה בצד לקוח
    ) {
        return persist.assignTask(token, taskId, clientId, usernames, hours);
    }


    @PostMapping("/clients/update")
    public BasicResponse updateClient(
            @RequestParam String token,
            @RequestParam int clientId,
            @RequestParam String name,
            @RequestParam int managerId
    ) {
        return persist.updateClient(token, clientId, name, managerId);
    }
    @PostMapping("/deleteClient")
    public ResponseEntity<Void> deleteClient(
            @RequestParam String token,
            @RequestParam int clientId
    ) {
        BasicResponse resp = persist.deleteClientById(token, clientId);
        if (!resp.isSuccess()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/get-tasks")
    public List<TaskAssignmentView> getTasks(@RequestParam("token") String token) {
        UserEntity user = persist.getUserByPass(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        int roleId = user.getRole().getId();
        List<TaskAssignmentEntity> raw;

        if (roleId == 3 || roleId == 4) {
            raw = persist.getAllTaskAssignments();
        } else if (roleId == 2) {
            raw = persist.getTaskAssignmentsByTeamId(user.getTeamId());
        } else {
            raw = persist.getTaskAssignmentsByUserId(user.getId());
        }

        return raw.stream().map(ta -> {
            List<AssignmentParticipantEntity> parts =
                    persist.getParticipantsForAssignment(ta.getId());
            return TaskAssignmentView.fromEntity(ta, parts);
        }).toList();
    }


    // POST /tasks/{id}/complete?token=XYZ
    @PostMapping("/tasks/{id}/complete")
    public void completeTask(
            @PathVariable int id,
            @RequestParam("token") String token) {

        UserEntity user = persist.getUserByPass(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        int roleId = user.getRole().getId();
        boolean allowed = roleId == 3
                || roleId ==4
                || roleId == 2
                || persist.getTaskOwner(id) == user.getId();

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        persist.markTaskCompleted(id);
    }
    // DTO קטן ל‐request של עדכון עובד
    public static class WorkerUpdateRequest {
        public String username;
        public List<String> abilities;
        public Integer teamId;
        // getters/setters אם צריך
    }

    @PostMapping("/update-worker")
    public BasicResponse updateWorker(
            @RequestParam String token,
            @RequestBody Map<String, Object> body
    ) {
        String username = (String) body.get("username");
        @SuppressWarnings("unchecked")
        List<String> newAbilities = (List<String>) body.get("abilities");

        if (username == null || username.isBlank()) {
            BasicResponse resp = new BasicResponse(false, Constants.FAIL);
            resp.setMessage("username חסר");
            return resp;
        }

        // שמירת היכולות על המשתמש הנבחר (לא זה שמחובר!)
        UserEntity target = persist.getUserByUsername(username); // מחזיר רק Active
        if (target == null) {
            BasicResponse resp = new BasicResponse(false, Constants.FAIL);
            resp.setMessage("עובד לא נמצא");
            return resp;
        }
        String abilitiesAsString = String.join(",", newAbilities == null ? List.of() : newAbilities);
        persist.save(new UserAbilityEntity(target.getId(), abilitiesAsString));

        // דגל קידום לאדמין
        boolean makeAdmin = false;
        Object mk = body.get("makeAdmin");
        if (mk instanceof Boolean b) makeAdmin = b;
        else if (mk instanceof String s) makeAdmin = Boolean.parseBoolean(s);

        // Persist יבדוק הרשאות ויבצע קידום (1→3, 2→4)
        return persist.updateWorker(token, username, newAbilities, makeAdmin);
    }




    @DeleteMapping("/delete-worker")
    @Transactional
    public BasicResponse deleteWorker(@RequestParam String token, @RequestParam String username) {
        BasicResponse resp = new BasicResponse();
        UserEntity admin = persist.getUserByPass(token);
        int roleId = (admin != null ? admin.getRole().getId() : -1);

        // גם 3 וגם 4 נחשבים מורשים
        if (admin == null || (roleId != 3 && roleId != 4)) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("אין הרשאה");
            return resp;
        }

        UserEntity u = persist.getUserByUsernameIncludingDeleted(username); // גרסה שלא מסננת
        if (u == null) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("עובד לא נמצא");
            return resp;
        }

        // מחיקה רכה:
        u.setStatus("DELETED");
        u.setOtp("");                 // לא לאפשר OTP ישן
        u.setPasswordRecovery("");    // נטרול קוד התאוששות אם קיים
        persist.save(u);

        resp.setSuccess(true);
        resp.setErrorCode(Constants.SUCCESS);
        return resp;
    }


    @PostMapping("/clients/update-by-username")
    public BasicResponse updateClientByUsername(
            @RequestParam String token,
            @RequestParam int clientId,
            @RequestParam String name,
            @RequestParam String managerUsername
    ) {

        int managerId = persist.getUserIdByUsername(managerUsername);

        // 3. קריאה לפונקציית העדכון הקיימת
        return persist.updateClient(token, clientId, name, managerId);
    }
    @GetMapping("/user-abilities")
    public List<String> getUserAbilities(@RequestParam String token) {
        UserEntity user = persist.getUserByPass(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        return persist.getUserAbilities(user.getId());
    }


    @PostMapping("/tasks/update")
    public BasicResponse updateTask(
            @RequestParam String token,
            @RequestBody Map<String, Object> body
    ) {
        System.out.println("updateTask body = " + body);
        System.out.println("updateTask token = " + token);

        BasicResponse resp = new BasicResponse();
        UserEntity me = userByToken(token);
        int role = (me != null ? me.getRole().getId() : -1);
        // authorization: only roles 3 or 4 allowed
        if (me == null || (role != 3 && role != 4)) {
            System.out.println("Authorization failed: role=" + role);
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }

        try {
            int id = (Integer) body.get("id");
            String name = (String) body.get("name");

            Number avgNum = (Number) body.get("averageTime");
            int avg = avgNum.intValue();

            @SuppressWarnings("unchecked")
            List<String> requires = (List<String>) body.get("requires");

            persist.updateTaskWithRequirements(id, name, avg, requires);

            resp.setSuccess(true);
            resp.setErrorCode(Constants.SUCCESS);
        } catch (Exception e) {
            e.printStackTrace();
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
        }
        return resp;
    }


    // 4. מחיקת משימה
    @PostMapping("/tasks/{id}/delete")
    public BasicResponse deleteTask(
            @RequestParam String token,
            @PathVariable int id
    ) {
        BasicResponse resp = new BasicResponse();
        UserEntity me = userByToken(token);
        int roleId = (me != null ? me.getRole().getId() : -1);

// בדיקת הרשאות: רק תפקידים 3 או 4 מורשים
        if (me == null || (roleId != 3 && roleId != 4)) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }
        try {
            // soft delete: מסמן סטטוס כ־DELETED
            persist.softDeleteTask(id);
            resp.setSuccess(true);
            resp.setErrorCode(Constants.SUCCESS);
        } catch (Exception e) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
        }
        return resp;
    }
    @GetMapping("/my-team")
    public Object myTeam(@RequestParam String token) {
        UserEntity me = persist.getUserByPass(token);
        if (me == null) {
            BasicResponse err = new BasicResponse(false, 401);
            err.setMessage("Invalid token");
            return err;
        }
        int role = me.getRole().getId();
        if (role != 2 && role != 4) {
            BasicResponse err = new BasicResponse(false, 403);
            err.setMessage("Not a team leader");
            return err;
        }

        // טוענים את כל החברים באותו צוות
        List<UserEntity> members = persist.loadList(UserEntity.class).stream()
                .filter(u -> u.getTeamId() == me.getTeamId())
                .collect(Collectors.toList());

        List<TeamMemberDTO> dto = members.stream().map(u -> {
            TeamMemberDTO m = new TeamMemberDTO();
            m.setUsername(u.getUsername());

            // סופרים את כל השעות המתוּרמות מהטבלה החדשה AssignmentParticipantEntity
            int totalHours = persist
                    .getParticipantsForUserId(u.getId())            // שיטה חדשה שתשלוף את כל ה-AssignmentParticipantEntity לפי user_id
                    .stream()
                    .mapToInt(AssignmentParticipantEntity::getHours)
                    .sum();
            m.setHoursWorked(totalHours);

            // מוציאים את שמות הלקוחות שהוא מנהל
            List<String> clientNames = persist.getAllClientResponses().stream()
                    .filter(c -> c.getManagerUsername().equals(u.getUsername()))
                    .map(ClientResponse::getName)
                    .collect(Collectors.toList());
            m.setClients(clientNames);

            return m;
        }).collect(Collectors.toList());

        return dto;
    }

    // GeneralController.java
    @PostMapping("/tasks/{id}/log-hours")
    public BasicResponse logTaskHours(
            @PathVariable int id,
            @RequestParam String token,
            @RequestParam String username,
            @RequestParam int hours
    ) {
        return persist.logHours(id, token, username, hours);
    }
    @GetMapping("/tasks/{id}/participants")
    public List<AssignmentParticipantResponse> getParticipants(
            @PathVariable int id,
            @RequestParam String token
    ) {
        // … קיימת בדיקת token …
        return persist.getParticipantsForAssignment(id).stream()
                .map(p -> new AssignmentParticipantResponse(
                        p.getUser().getUsername(),
                        p.getHours()          // או p.getHoursAssigned(), תלוי איך הגדרת ב־Entity
                ))
                .toList();
    }


}
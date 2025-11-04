package com.ashcollege.service;


import com.ashcollege.entities.*;
import com.ashcollege.responses.*;
import com.ashcollege.utils.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ashcollege.entities.UserEntity;

import java.util.*;
import java.util.stream.Collectors;


@Transactional
@Component
@SuppressWarnings("unchecked")
public class Persist {
    private final SessionFactory sessionFactory;

    private final ObjectMapper json = new ObjectMapper();


    private static final Logger LOGGER = LoggerFactory.getLogger(Persist.class);



    @Autowired
    public Persist(SessionFactory sf) {
        this.sessionFactory = sf;
    }
    public <T> void saveAll(List<T> objects) {
        for (T object : objects) {
            sessionFactory.getCurrentSession().saveOrUpdate(object);
        }
    }
    public <T> void remove(Object o){
        sessionFactory.getCurrentSession().remove(o);
    }


    public Session getQuerySession() {
        return sessionFactory.getCurrentSession();
    }

    public void save(Object object) {
        this.sessionFactory.getCurrentSession().saveOrUpdate(object);
    }

    public <T> T loadObject(Class<T> clazz, int oid) {
        return this.getQuerySession().get(clazz, oid);
    }

    public <T> List<T> loadList(Class<T> clazz)
    {
        return this.sessionFactory.getCurrentSession()
                .createQuery("FROM " + clazz.getSimpleName()).list();
    }



    public UserEntity getUserByUsernameAndPass(String username, String hash) {
        var session = getQuerySession();
        return session.createQuery(
                        "FROM UserEntity u WHERE u.username=:u AND u.password=:p AND (u.status IS NULL OR u.status='')",
                        UserEntity.class
                )
                .setParameter("u", username)
                .setParameter("p", hash)
                .uniqueResult();
    }


    public UserEntity getUserByEmail(String email) {
        return this.sessionFactory.getCurrentSession()
                .createQuery("FROM UserEntity WHERE email = :email ", UserEntity.class)
                .setParameter("email", email)
                .uniqueResult();
    }

    public UserEntity getUserByEmailAndPasswordRecovery(String email, String code) {
        var s = getQuerySession();
        return s.createQuery(
                        "FROM UserEntity u WHERE u.email=:e AND u.passwordRecovery=:c AND (u.status IS NULL OR u.status='')",
                        UserEntity.class
                )
                .setParameter("e", email)
                .setParameter("c", code)
                .uniqueResult();
    }

    public UserEntity getUserByUsername(String username) {
        var s = getQuerySession();
        return s.createQuery(
                        "FROM UserEntity u WHERE u.username=:u AND (u.status IS NULL OR u.status='')",
                        UserEntity.class
                )
                .setParameter("u", username)
                .uniqueResult();
    }


    public UserEntity getUserByPass(String tokenHash) {
        var session = getQuerySession();
        return session.createQuery(
                        "FROM UserEntity u WHERE u.password=:p AND (u.status IS NULL OR u.status='')",
                        UserEntity.class
                )
                .setParameter("p", tokenHash)
                .uniqueResult();
    }


    public AdminEntity getLIdByUserId(int userId) {
        return this.sessionFactory.getCurrentSession()
                .createQuery("FROM AdminEntity  WHERE user_id= :user_id" , AdminEntity.class)
                .setParameter("user_id",userId)
                .uniqueResult();
    }

    public int getUserIdByPass(String password) {
        try {
            return this.sessionFactory.getCurrentSession()
                    .createQuery("SELECT id FROM UserEntity WHERE password = :password", Integer.class)
                    .setParameter("password", password)
                    .uniqueResult();
        } catch (Exception e) {
            throw new RuntimeException("Error fetching user ID by password", e);
        }
    }
    public void saveUserWithAbilities(UserEntity user, String abilitiesStr) {
        Session session = sessionFactory.getCurrentSession();

        // שמירת המשתמש
        session.persist(user);
        session.flush(); // מבטיח שה-ID נוצר

        // שמירת היכולות כמחרוזת
        if (abilitiesStr != null && !abilitiesStr.isEmpty()) {
            UserAbilityEntity ua = new UserAbilityEntity(user.getId(), abilitiesStr);
            session.persist(ua);
        }
    }
    public List<UserEntity> loadActiveUsers() {
        var s = getQuerySession();
        return s.createQuery(
                "FROM UserEntity u WHERE (u.status IS NULL OR u.status='')",
                UserEntity.class
        ).list();
    }
    // Persist.java
    public UserEntity getUserByUsernameIncludingDeleted(String username) {
        var s = getQuerySession();
        return s.createQuery(
                        "FROM UserEntity u WHERE u.username = :u",
                        UserEntity.class
                )
                .setParameter("u", username)
                .uniqueResult();
    }


    public List<Map<String, String>> getEligibleWorkers() {
        return this.sessionFactory.getCurrentSession()
                .createQuery(
                        "FROM UserEntity u " +
                                "WHERE (u.role.id = 1 OR u.role.id = 3) " +
                                "AND (u.status IS NULL OR u.status = '')",
                        UserEntity.class
                )
                .list()
                .stream()
                .map(u -> Map.of(
                        "username", u.getUsername(),
                        "team", String.valueOf(u.getTeamId())
                ))
                .collect(Collectors.toList());
    }

    public List<AbilityEntity> getAllAbilities() {
        Session session = sessionFactory.getCurrentSession();
        // simple HQL שמביא את כל הרשומות מהטבלה abilities
        return session.createQuery("from AbilityEntity", AbilityEntity.class)
                .getResultList();
    }
    public TaskEntity saveTaskWithRequirements(TaskRequest req) {
        Session session = sessionFactory.getCurrentSession();
        // 1. שמירת המשימה
        TaskEntity task = new TaskEntity();
        task.setName(req.getName());
        task.setAverageTime(req.getAverageTime());
        task.setStatus("1");
        session.save(task);

        // 2. שמירת דרישות כ–JSON בשדה abilities
        TaskRequirementEntity tr = new TaskRequirementEntity();
        tr.setTaskId(task.getId());
        try {
            tr.setAbilities(json.writeValueAsString(req.getRequires()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize requires", e);
        }
        session.save(tr);

        return task;
    }
    public AbilityEntity saveAbility(AbilityEntity ability) {
        Session session = sessionFactory.getCurrentSession();
        session.saveOrUpdate(ability);
        return ability;
    }

    public void deleteAbilityByName(String name) {
        Session session = sessionFactory.getCurrentSession();

        // 1. מחיקת מהטבלה של היכולות
        AbilityEntity a = session.createQuery(
                        "from AbilityEntity where name = :n", AbilityEntity.class)
                .setParameter("n", name)
                .uniqueResult();
        if (a != null) {
            session.delete(a);
        }

        // 2. ניקוי מהמשתמשים
        List<UserAbilityEntity> users = session.createQuery(
                "from UserAbilityEntity", UserAbilityEntity.class).list();
        for (UserAbilityEntity u : users) {
            if (u.getAbilities() == null) continue;
            List<String> updated = new ArrayList<>();
            for (String ab : u.getAbilities().split(",")) {
                if (!ab.trim().equals(name)) {
                    updated.add(ab.trim());
                }
            }
            u.setAbilities(String.join(",", updated));
            session.save(u);
        }

        // 3. ניקוי מהמשימות
        List<TaskRequirementEntity> tasks = session.createQuery(
                "from TaskRequirementEntity", TaskRequirementEntity.class).list();
        for (TaskRequirementEntity task : tasks) {
            String raw = task.getAbilities();
            if (raw == null || raw.isEmpty()) continue;

            try {
                JSONArray arr = new JSONArray(raw);
                JSONArray updated = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    String ability = arr.getString(i);
                    if (!ability.equals(name)) {
                        updated.put(ability);
                    }
                }
                task.setAbilities(updated.toString());
                session.save(task);
            } catch (Exception e) {
                System.err.println("Bad JSON in task: " + task.getTaskId() + " " + raw);
            }
        }
    }

    public List<TaskResponse> getAllTasksWithRequirements() {
        Session session = sessionFactory.getCurrentSession();
        // רק משימות שהסטטוס שלהן אינו 'DELETED'
        List<TaskEntity> tasks = session
                .createQuery(
                        "from TaskEntity t where t.status <> 'DELETED'",
                        TaskEntity.class
                )
                .getResultList();

        return tasks.stream().map(task -> {
            TaskRequirementEntity tr = session.get(
                    TaskRequirementEntity.class,
                    task.getId()
            );

            List<String> requires = Collections.emptyList();
            if (tr != null && tr.getAbilities() != null) {
                String abil = tr.getAbilities().trim();
                if (abil.startsWith("[")) {
                    // JSON array
                    try {
                        requires = json.readValue(
                                abil,
                                new TypeReference<List<String>>() {}
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to parse JSON abilities for task " + task.getId(), e
                        );
                    }
                } else {
                    // comma-separated fallback
                    requires = Arrays.stream(abil.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }
            }

            return new TaskResponse(
                    task.getId(),
                    task.getName(),
                    task.getAverageTime(),
                    requires
            );
        }).collect(Collectors.toList());
    }

    /** 2. כל הלקוחות */
    public List<ClientResponse> getAllClients() {
        Session session = sessionFactory.getCurrentSession();
        return session.createQuery(
                        "from ClientEntity c where c.status <> 'DELETED'",
                        ClientEntity.class)
                .getResultList()
                .stream()
                .map(c -> new ClientResponse(
                        c.getId(),
                        c.getName(),
                        c.getManager().getUsername(),
                        c.getManager().getTeamId()
                ))
                .collect(Collectors.toList());
    }



    /** סכום השעות הפתוחות (status != 'completed') לכל משתמש */
    public Map<String, Integer> getPendingHoursByUser() {
        Session s = sessionFactory.getCurrentSession();
        // Group by username, sum hours
        List<Object[]> rows = s.createQuery(
                        "select t.user.username, coalesce(sum(t.hours),0) " +
                                "  from TaskAssignmentEntity t " +
                                " where t.status <> 'completed' " +
                                " group by t.user.username", Object[].class)
                .getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).intValue()
        ));
    }

    /** כל העובדים בעלי role 1 או 2, כולל hoursWorked מחשבון העומס הפתוח ו־abilities */
    public List<WorkerDto> getAllWorkersWithData() {
        Session s = sessionFactory.getCurrentSession();
        Map<String,Integer> pendingHours = getPendingHoursByUser();

        List<UserEntity> users = s.createQuery(
                "from UserEntity u " +
                        "where u.role.id in (1,2,3,4) " +
                        "and (u.status is null or u.status = '')",
                UserEntity.class
        ).getResultList();

        return users.stream().map(u -> {
            UserAbilityEntity ua = s.get(UserAbilityEntity.class, u.getId());
            List<String> abil = (ua != null && ua.getAbilities() != null)
                    ? Arrays.stream(ua.getAbilities().split(","))
                    .map(String::trim).filter(x -> !x.isEmpty()).toList()
                    : List.of();

            return new WorkerDto(
                    u.getUsername(),
                    u.getRole().getId(),
                    u.getTeamId(),
                    abil,
                    pendingHours.getOrDefault(u.getUsername(), 0)
            );
        }).toList();
    }


    /** דרישות המשימה (parsing JSON array or comma-list fallback) */
    public List<String> getTaskRequirements(int taskId) {
        Session s = sessionFactory.getCurrentSession();
        TaskRequirementEntity tr = s.get(TaskRequirementEntity.class, taskId);
        if (tr == null || tr.getAbilities() == null) return List.of();

        String raw = tr.getAbilities().trim();
        if (raw.startsWith("[")) {
            try {
                return json.readValue(raw, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                throw new RuntimeException("JSON parse error for task " + taskId, e);
            }
        }
        // comma-separated fallback
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .toList();
    }
    public List<AssignmentParticipantEntity> getParticipantsForAssignment(int assignmentId) {
        return sessionFactory.getCurrentSession()
                .createQuery(
                        "FROM AssignmentParticipantEntity ap WHERE ap.task.id = :aid",
                        AssignmentParticipantEntity.class
                )
                .setParameter("aid", assignmentId)
                .getResultList();
    }

    // ב־Persist.java
    // Persist.java
    @Transactional
    public BasicResponse assignTask(
            String token,
            int taskId,
            int clientId,
            List<String> usernames,
            int hoursRequired       // <-- השעה שהוזנה
    ) {
        BasicResponse resp = new BasicResponse();

        // בדיקת הרשאה
        UserEntity admin = getUserByPass(token);
        int roleId = (admin != null) ? admin.getRole().getId() : -1;
        if (admin == null || (roleId != 3 && roleId != 4)) {
            resp.setSuccess(false);
            return resp;
        }

        Session s = sessionFactory.getCurrentSession();
        TaskEntity   task   = s.get(TaskEntity.class,   taskId);
        ClientEntity client = s.get(ClientEntity.class, clientId);
        if (task == null || client == null) {
            resp.setSuccess(false);
            return resp;
        }

        // שליפת כל המשתמשים לפי שמות
        List<UserEntity> users = usernames.stream()
                .map(this::getUserByUsername)
                .filter(Objects::nonNull)
                .toList();
        if (users.isEmpty()) {
            resp.setSuccess(false);
            return resp;
        }

        // מציאת בעל התפקיד הגבוה ביותר
        UserEntity owner = users.stream()
                .max(Comparator.comparingInt(u -> u.getRole().getId()))
                .get();

        // יצירת TaskAssignment יחיד עם השעה הנדרשת
        TaskAssignmentEntity ta = new TaskAssignmentEntity();
        ta.setTask(task);
        ta.setClient(client);
        ta.setUser(owner);
        ta.setHours(hoursRequired);       // <-- שמירת hours_required
        ta.setStatus("assigned");
        ta.setAssignedDate(new Date());
        s.save(ta);  // id מתעדכן כאן

        // יצירת AssignmentParticipant לכל עובד עם שעות התחלתי 0
        for (UserEntity u : users) {
            AssignmentParticipantEntity ap = new AssignmentParticipantEntity();
            ap.setTask(ta);
            ap.setUser(u);
            ap.setHours(0);
            s.save(ap);
        }

        resp.setSuccess(true);
        return resp;
    }



    public BasicResponse updateClient(
            String token,
            int clientId,
            String newName,
            int newManagerId
    ) {
        BasicResponse resp = new BasicResponse();

        // 1. הרשאה (רק אדמין)
        UserEntity admin = getUserByPass(token);
        int roleId = (admin != null ? admin.getRole().getId() : -1);
// רק תפקידים 3 או 4 מורשים
        if (admin == null || (roleId != 3 && roleId != 4)) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("אין הרשאה");
            return resp;
        }

        Session session = sessionFactory.getCurrentSession();
        ClientEntity client  = session.get(ClientEntity.class, clientId);
        UserEntity   manager = session.get(UserEntity.class,   newManagerId);

        if (client == null) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("לקוח לא נמצא");
            return resp;
        }
        if (manager == null) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("משתמש מנהל לא נמצא");
            return resp;
        }

        // 2. בצע את העדכונים ושמור
        client.setName(newName);
        client.setManager(manager);
        session.saveOrUpdate(client);

        resp.setSuccess(true);
        resp.setErrorCode(Constants.SUCCESS);
        return resp;
    }

    public BasicResponse deleteClientById(String token, int clientId) {
        BasicResponse resp = new BasicResponse();

        // בדיקת הרשאה: רק אדמין (role ID = 3 או 4) מורשים
        UserEntity admin = getUserByPass(token);
        int roleId = (admin != null ? admin.getRole().getId() : -1);
        if (admin == null || (roleId != 3 && roleId != 4)) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("אין הרשאה");
            return resp;
        }

        Session session = sessionFactory.getCurrentSession();
        ClientEntity client = session.get(ClientEntity.class, clientId);
        if (client == null) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("לקוח לא נמצא");
            return resp;
        }

        // soft delete: סימון סטטוס במקום מחיקה פיזית
        client.setStatus("DELETED");
        // השינויים יתועדו אוטומטית בטרנזקציה

        resp.setSuccess(true);
        resp.setErrorCode(Constants.SUCCESS);
        return resp;
    }
    public List<TaskAssignmentEntity> getAllTaskAssignments() {
        return sessionFactory.getCurrentSession()
                .createQuery("from TaskAssignmentEntity", TaskAssignmentEntity.class)
                .list();
    }

    public List<TaskAssignmentEntity> getTaskAssignmentsByUserId(int userId) {
        return sessionFactory.getCurrentSession()
                .createQuery("from TaskAssignmentEntity where user.id = :uid", TaskAssignmentEntity.class)
                .setParameter("uid", userId)
                .list();
    }

    public List<TaskAssignmentEntity> getTaskAssignmentsByUserIds(List<Integer> userIds) {
        return sessionFactory.getCurrentSession()
                .createQuery("from TaskAssignmentEntity where user.id in (:uids)", TaskAssignmentEntity.class)
                .setParameterList("uids", userIds)
                .list();
    }

    public List<TeamMemberEntity> getTeamMembersByTeamId(int teamId) {
        return sessionFactory.getCurrentSession()
                .createQuery("from TeamMemberEntity where teamId = :tid", TeamMemberEntity.class)
                .setParameter("tid", teamId)
                .list();
    }
    public void markTaskCompleted(int id) {
        var session = sessionFactory.getCurrentSession();
        TaskAssignmentEntity e = session.get(TaskAssignmentEntity.class, id);
        e.setStatus("COMPLETED");
        session.update(e);
    }

    public int getTaskOwner(int id) {
        var session = sessionFactory.getCurrentSession();
        TaskAssignmentEntity e = session.get(TaskAssignmentEntity.class, id);
        return e.getUser().getId();
    }
    public int getUserIdByUsername(String username) {
        UserEntity u = getUserByUsername(username);
        return u != null ? u.getId() : -1;
    }
    // שמירת תאימות לאחור – לא משנה התנהגות קי

    // תאימות לאחור
    public BasicResponse updateWorker(String token, String username, List<String> abilities) {
        return updateWorker(token, username, abilities, false);
    }

    public BasicResponse updateWorker(
            String token,
            String username,
            List<String> abilities,
            boolean makeAdmin
    ) {
        BasicResponse resp = new BasicResponse();

        try {
            // הרשאה: 3 או 4
            UserEntity actor = getUserByPass(token);
            int roleId = (actor != null ? actor.getRole().getId() : -1);
            if (actor == null || (roleId != 3 && roleId != 4)) {
                resp.setSuccess(false);
                resp.setErrorCode(Constants.FAIL);
                resp.setMessage("אין הרשאה");
                return resp;
            }

            Session s = sessionFactory.getCurrentSession();
            // יעד: פעיל בלבד
            UserEntity target = getUserByUsername(username);
            if (target == null) {
                resp.setSuccess(false);
                resp.setErrorCode(Constants.FAIL);
                resp.setMessage("עובד לא נמצא");
                return resp;
            }

            // קידום לאדמין אם התבקש: 1→3, 2→4 (החלפת ה־RoleEntity ולא שינוי ה־id)
            if (makeAdmin) {
                int cur = target.getRole().getId();
                if (cur == 1) {
                    RoleEntity newRole = new RoleEntity();
                    newRole.setId(3);           // admin
                    target.setRole(newRole);
                    s.saveOrUpdate(target);
                } else if (cur == 2) {
                    RoleEntity newRole = new RoleEntity();
                    newRole.setId(4);           // superadmin
                    target.setRole(newRole);
                    s.saveOrUpdate(target);
                }
                // אם כבר 3/4 – אין שינוי
            }

            resp.setSuccess(true);
            resp.setErrorCode(Constants.SUCCESS);
            return resp;

        } catch (Exception e) {
            // נחזיר שגיאה "רכה" כדי שתראה הודעה במקום 500
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            resp.setMessage("שגיאה בעדכון עובד: " + e.getMessage());
            return resp;
        }
    }


    public List<String> getUserAbilities(int userId) {
        Session s = sessionFactory.getCurrentSession();
        UserAbilityEntity ua = s.get(UserAbilityEntity.class, userId);
        if (ua == null || ua.getAbilities() == null || ua.getAbilities().isBlank()) {
            return List.of();
        }
        String raw = ua.getAbilities().trim();
        // אם זה JSON array
        if (raw.startsWith("[")) {
            try {
                return json.readValue(raw, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                throw new RuntimeException("שגיאת JSON בפירוק יכולות עבור משתמש " + userId, e);
            }
        }
        // אחרת – מחרוזת מופרדת בפסיקים
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .toList();
    }
    public void removeAbilityFromUsers(String ability) {
        Session session = sessionFactory.getCurrentSession();
        // Load all entries
        List<UserAbilityEntity> list = session.createQuery(
                        "from UserAbilityEntity", UserAbilityEntity.class)
                .getResultList();
        for (UserAbilityEntity ua : list) {
            String raw = ua.getAbilities();
            if (raw == null || raw.isBlank()) continue;
            List<String> parts = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.equalsIgnoreCase(ability) && !s.isEmpty())
                    .collect(Collectors.toList());
            if (parts.isEmpty()) {
                session.delete(ua);
            } else {
                ua.setAbilities(String.join(",", parts));
                session.saveOrUpdate(ua);
            }
        }
    }
    // 1. עדכון משימה עם דרישות
    public void updateTaskWithRequirements(
            int taskId,
            String newName,
            int newAvgTime,
            List<String> newRequires
    ) {
        Session session = sessionFactory.getCurrentSession();

        // 1. עדכון TaskEntity
        TaskEntity task = session.get(TaskEntity.class, taskId);
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }
        task.setName(newName);
        task.setAverageTime(newAvgTime);

        // 2. עדכון או יצירת TaskRequirementEntity באמצעות HQL
        TaskRequirementEntity tr = session.createQuery(
                        "from TaskRequirementEntity tr where tr.taskId = :tid",
                        TaskRequirementEntity.class)
                .setParameter("tid", taskId)
                .uniqueResult();

        String abilitiesJson;
        try {
            abilitiesJson = new ObjectMapper().writeValueAsString(newRequires);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize requires", e);
        }

        if (tr == null) {
            tr = new TaskRequirementEntity();
            tr.setTaskId(taskId);
            tr.setAbilities(abilitiesJson);
            session.persist(tr);
        } else {
            tr.setAbilities(abilitiesJson);
            session.saveOrUpdate(tr);
        }
    }

    // 2. מחיקת משימה (כולל דרישות)
    public void deleteTaskById(int taskId) {
        Session session = sessionFactory.getCurrentSession();
        // מחיקת דרישות תחילה
        TaskRequirementEntity tr = session.get(TaskRequirementEntity.class, taskId);
        if (tr != null) session.delete(tr);
        // מחיקת המשימה
        TaskEntity task = session.get(TaskEntity.class, taskId);
        if (task != null) session.delete(task);
    }
    @SuppressWarnings("unchecked")
    public List<TaskAssignmentEntity> getTaskAssignmentsByTeamId(int teamId) {
        return sessionFactory
                .getCurrentSession()
                .createQuery("from TaskAssignmentEntity ta where ta.user.teamId = :teamId")
                .setParameter("teamId", teamId)
                .list();
    }
    public int getHoursWorkedByUserId(int userId) {
        Long sum = sessionFactory.getCurrentSession()
                .createQuery(
                        "select sum(t.hours) from TaskAssignmentEntity t where t.user.id = :uid",
                        Long.class)
                .setParameter("uid", userId)
                .uniqueResult();
        return sum == null ? 0 : sum.intValue();
    }

    // 2. קריאה ללקוחות עם managerTeamId
    public List<ClientResponse> getAllClientResponses() {
        return sessionFactory.getCurrentSession()
                .createQuery("select new com.ashcollege.responses.ClientResponse(" +
                        " c.id, c.name, c.manager.username, c.manager.teamId ) " +
                        "from ClientEntity c", ClientResponse.class)
                .list();
    }

    public UserEntity getUserByPhone(String phone) {
        return sessionFactory.getCurrentSession()
                .createQuery("from UserEntity u where u.phoneNumber = :p", UserEntity.class)
                .setParameter("p", phone)
                .uniqueResult();
    }

    public List<ClientEntity> getClientsByManager(String managerUsername) {
        return sessionFactory.getCurrentSession()
                .createQuery("from ClientEntity c where c.manager.username = :m", ClientEntity.class)
                .setParameter("m", managerUsername)
                .list();
    }
    public List<TeamEntity> getAllTeams() {
        return loadList(TeamEntity.class);
    }

    public BasicResponse renameTeam(int teamId, String newName) {
        BasicResponse resp = new BasicResponse();
        TeamEntity team = loadObject(TeamEntity.class, teamId);
        if (team == null) {
            resp.setSuccess(false);
            resp.setMessage("קבוצה לא נמצאה");
            return resp;
        }
        team.setName(newName.trim());
        save(team);
        resp.setSuccess(true);
        return resp;
    }

    public BasicResponse addMemberToTeam(int teamId, String username) {
        BasicResponse resp = new BasicResponse();
        UserEntity u = getUserByUsername(username);
        if (u == null) {
            resp.setSuccess(false);
            resp.setMessage("עובד לא נמצא");
            return resp;
        }
        int role = u.getRole().getId();
        if (role != 1 && role != 3) {
            resp.setSuccess(false);
            resp.setMessage("עובד חייב להיות role=1 או 3");
            return resp;
        }
        u.setTeamId(teamId);
        save(u);
        resp.setSuccess(true);
        return resp;
    }

    public BasicResponse changeTeamLeader(int teamId, String newLeaderUsername) {
        BasicResponse resp = new BasicResponse();
        TeamEntity team = loadObject(TeamEntity.class, teamId);
        if (team == null) {
            resp.setSuccess(false);
            resp.setMessage("קבוצה לא נמצאה");
            return resp;
        }

        UserEntity oldLeader = team.getHead();
        UserEntity newLeader = getUserByUsername(newLeaderUsername);
        if (newLeader == null || newLeader.getTeamId() != teamId) {
            resp.setSuccess(false);
            resp.setMessage("המועמד לראש חייב כבר להיות חבר בקבוצה");
            return resp;
        }

        // קידום המועמד
        int newRole = newLeader.getRole().getId() == 3 ? 4 : 2;  // 3->4, 1->2
        newLeader.getRole().setId(newRole);
        save(newLeader);

        // הדחה של הישן
        int oldRole = oldLeader.getRole().getId();
        int demoted = (oldRole == 2 ? 1 : (oldRole == 4 ? 3 : oldRole));
        oldLeader.getRole().setId(demoted);
        save(oldLeader);

        // עדכון הקבוצה
        team.setHead(newLeader);
        save(team);

        resp.setSuccess(true);
        resp.setMessage("ראש הקבוצה הוחלף בהצלחה");
        return resp;
    }

    public BasicResponse deleteTeamIfEmpty(int teamId) {
        BasicResponse resp = new BasicResponse();
        TeamEntity team = loadObject(TeamEntity.class, teamId);
        if (team == null) {
            resp.setSuccess(false);
            return resp;
        }
        // בודק שיש רק ראש ולא חברים
        long countMembers = loadList(UserEntity.class).stream()
                .filter(u -> u.getTeamId() == teamId && !u.equals(team.getHead()))
                .count();
        if (countMembers > 0) {
            resp.setSuccess(false);
            return resp;
        }

        // הדחה של הראש לחוזר ל־worker/admin
        UserEntity head = team.getHead();
        int headRole = head.getRole().getId();
        head.getRole().setId(headRole == 2 ? 1 : (headRole == 4 ? 3 : headRole));
        head.setTeamId(0);
        save(head);

        // מחיקת הקבוצה
        getQuerySession().delete(team);

        resp.setSuccess(true);
        return resp;
    }
    @Transactional
    public BasicResponse logHours(int assignmentId, String token, String username, int hours) {
        BasicResponse resp = new BasicResponse();

        // 1. בדיקה בסיסית של שעות
        if (hours <= 0) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }

        // 2. Authentication
        UserEntity actor = getUserByPass(token);
        if (actor == null) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }

        Session s = sessionFactory.getCurrentSession();
        TaskAssignmentEntity ta = s.get(TaskAssignmentEntity.class, assignmentId);
        if (ta == null) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }

        // 3. Authorization
        UserEntity target = getUserByUsername(username);
        int role = actor.getRole().getId();
        if (target == null
                || (role == 1 && !actor.getUsername().equals(username))
                || (role == 2 && actor.getTeamId() != target.getTeamId())
        ) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }

        // 4. שליפת השורה המתאימה
        AssignmentParticipantEntity p = s.createQuery(
                        "FROM AssignmentParticipantEntity ap " +
                                " WHERE ap.task.id = :aid AND ap.user.username = :uname",
                        AssignmentParticipantEntity.class
                )
                .setParameter("aid", assignmentId)
                .setParameter("uname", username)
                .uniqueResult();

        if (p == null) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }

        // 5. חשב יתרת שעות
        int totalLoggedBefore = getParticipantsForAssignment(assignmentId)
                .stream()
                .mapToInt(AssignmentParticipantEntity::getHours)
                .sum();
        int alreadyLogged = p.getHours();
        int required = ta.getHours();
        int remaining = required - (totalLoggedBefore - alreadyLogged);
        if (hours > remaining) {
            resp.setSuccess(false);
            resp.setErrorCode(Constants.FAIL);
            return resp;
        }

        // 6. עדכון שעות המשתתף
        p.setHours(alreadyLogged + hours);
        s.saveOrUpdate(p);

        // 7. עדכון סטטוס משימה אם נסגרו כל השעות
        int totalLoggedAfter = totalLoggedBefore - alreadyLogged + p.getHours();
        if (totalLoggedAfter >= required) {
            ta.setStatus("completed");
            s.saveOrUpdate(ta);
        }

        // 8. החזרת הצלחה
        resp.setSuccess(true);
        resp.setErrorCode(Constants.SUCCESS);
        return resp;
    }
    public List<AssignmentParticipantEntity> getParticipantsForUserId(int userId) {
        Session session = sessionFactory.getCurrentSession();
        Query<AssignmentParticipantEntity> q = session.createQuery(
                "FROM AssignmentParticipantEntity p WHERE p.user.id = :uid",
                AssignmentParticipantEntity.class
        );
        q.setParameter("uid", userId);
        return q.list();
    }
    public void softDeleteTask(int taskId) {
        Session session = sessionFactory.getCurrentSession();
        TaskEntity task = session.get(TaskEntity.class, taskId);
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }
        task.setStatus("DELETED");
        // שינוי השדה יתועד אוטומטית באמצעות הטרנזקציה
    }




}
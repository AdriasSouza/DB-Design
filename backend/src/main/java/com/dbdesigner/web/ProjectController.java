package com.dbdesigner.web;

import com.dbdesigner.domain.Project;
import com.dbdesigner.domain.ProjectRepository;
import com.dbdesigner.schema.DdlGenerator;
import com.dbdesigner.schema.DdlImporter;
import com.dbdesigner.schema.OracleReverseEngineer;
import com.dbdesigner.schema.Schema;
import com.dbdesigner.web.Dtos.CreateProjectRequest;
import com.dbdesigner.web.Dtos.DdlRequest;
import com.dbdesigner.web.Dtos.ImportRequest;
import com.dbdesigner.web.Dtos.JdbcImportRequest;
import com.dbdesigner.web.Dtos.ProjectDto;
import com.dbdesigner.web.Dtos.ShareResponse;
import com.dbdesigner.web.Dtos.UpdateProjectRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProjectController {

    private static final String EMPTY_SCHEMA = "{\"tables\":[],\"relations\":[]}";

    private final ProjectRepository projects;
    private final ObjectMapper mapper;
    private final DdlGenerator ddlGenerator;
    private final DdlImporter ddlImporter;
    private final OracleReverseEngineer oracleReverseEngineer;

    public ProjectController(ProjectRepository projects, ObjectMapper mapper, DdlGenerator ddlGenerator,
                             DdlImporter ddlImporter, OracleReverseEngineer oracleReverseEngineer) {
        this.projects = projects;
        this.mapper = mapper;
        this.ddlGenerator = ddlGenerator;
        this.ddlImporter = ddlImporter;
        this.oracleReverseEngineer = oracleReverseEngineer;
    }

    @GetMapping("/projects")
    public List<ProjectDto> list(@AuthenticationPrincipal Long userId) {
        return projects.findByOwnerIdOrderByUpdatedAtDesc(userId).stream().map(p -> toDto(p, false)).toList();
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateProjectRequest req) {
        String dbType = req.dbType() == null ? "oracle" : req.dbType();
        return toDto(projects.save(new Project(userId, req.name().trim(), dbType, EMPTY_SCHEMA)), true);
    }

    @GetMapping("/projects/{id}")
    public ProjectDto get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return toDto(owned(id, userId), true);
    }

    @PutMapping("/projects/{id}")
    public ProjectDto update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                             @Valid @RequestBody UpdateProjectRequest req) {
        Project project = owned(id, userId);
        if (req.name() != null && !req.name().isBlank()) {
            project.setName(req.name().trim());
        }
        if (req.dbType() != null) {
            project.setDbType(req.dbType());
        }
        if (req.schema() != null) {
            project.setSchemaJson(req.schema().toString());
        }
        return toDto(projects.save(project), false);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        projects.delete(owned(id, userId));
    }

    @PostMapping("/projects/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto clone(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        Project source = owned(id, userId);
        Project copy = new Project(userId, source.getName() + " (cópia)", source.getDbType(), source.getSchemaJson());
        return toDto(projects.save(copy), false);
    }

    @PostMapping("/projects/{id}/share")
    public ShareResponse share(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        Project project = owned(id, userId);
        if (project.getShareToken() == null) {
            project.setShareToken(UUID.randomUUID().toString().replace("-", ""));
            projects.save(project);
        }
        return new ShareResponse(project.getShareToken());
    }

    @DeleteMapping("/projects/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshare(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        Project project = owned(id, userId);
        project.setShareToken(null);
        projects.save(project);
    }

    @GetMapping("/public/{token}")
    public ProjectDto shared(@PathVariable String token) {
        Project project = projects.findByShareToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toDto(project, true);
    }

    @PostMapping(value = "/ddl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String ddl(@Valid @RequestBody DdlRequest req) {
        return ddlGenerator.generate(readSchema(req.schema()), req.dbType());
    }

    @GetMapping(value = "/projects/{id}/ddl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String projectDdl(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                             @RequestParam(required = false) String dbType) {
        Project project = owned(id, userId);
        return ddlGenerator.generate(readSchema(parse(project.getSchemaJson())),
                dbType == null ? project.getDbType() : dbType);
    }

    @PostMapping("/import/ddl")
    public Schema importDdl(@Valid @RequestBody ImportRequest req) {
        Schema schema = ddlImporter.parse(req.sql());
        if (schema.tables().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Nenhum CREATE TABLE encontrado");
        }
        return schema;
    }

    /** Engenharia reversa: a senha é usada só nesta requisição e nunca é armazenada nem registrada em log. */
    @PostMapping("/import/jdbc")
    public Schema importJdbc(@Valid @RequestBody JdbcImportRequest req) {
        try {
            return oracleReverseEngineer.read(new OracleReverseEngineer.Connect(
                    req.host().trim(), req.port(), req.service().trim(), req.sid(),
                    req.user().trim(), req.password(), req.schema()));
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Falha ao ler o banco Oracle: " + firstLine(e.getMessage()));
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "erro desconhecido";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }

    private Project owned(Long id, Long userId) {
        return projects.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projeto não encontrado"));
    }

    private ProjectDto toDto(Project p, boolean withSchema) {
        JsonNode schema = parse(p.getSchemaJson());
        int tables = schema.path("tables").size();
        int fields = 0;
        for (JsonNode t : schema.path("tables")) {
            fields += t.path("columns").size();
        }
        return new ProjectDto(p.getId(), p.getName(), p.getDbType(), p.getUpdatedAt(), tables, fields,
                p.getShareToken(), withSchema ? schema : null);
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Schema armazenado inválido");
        }
    }

    private Schema readSchema(JsonNode node) {
        try {
            return mapper.treeToValue(node == null ? parse(EMPTY_SCHEMA) : node, Schema.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schema inválido");
        }
    }
}

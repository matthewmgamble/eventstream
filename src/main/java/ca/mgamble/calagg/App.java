package ca.mgamble.calagg;

import ca.mgamble.calagg.config.AppConfig;
import ca.mgamble.calagg.config.DataSource;
import ca.mgamble.calagg.controller.ApiController;
import ca.mgamble.calagg.controller.DashboardController;
import ca.mgamble.calagg.controller.FeedController;
import ca.mgamble.calagg.mcp.McpToolHandler;
import ca.mgamble.calagg.mcp.McpTransport;
import ca.mgamble.calagg.repository.EventRepository;
import ca.mgamble.calagg.repository.FeedRepository;
import ca.mgamble.calagg.repository.OfficeLocationRepository;
import ca.mgamble.calagg.scheduler.SyncScheduler;
import ca.mgamble.calagg.service.FreeTimeService;
import ca.mgamble.calagg.service.IcsSyncService;
import ca.mgamble.calagg.service.PruneService;
import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        DataSource dataSource = new DataSource(config);

        FeedRepository feedRepository = new FeedRepository(dataSource);
        EventRepository eventRepository = new EventRepository(dataSource);
        ca.mgamble.calagg.config.OfficeConfig officeConfig = new ca.mgamble.calagg.config.OfficeConfig(config);
        OfficeLocationRepository officeLocationRepository = new OfficeLocationRepository(dataSource, officeConfig);

        // Services
        IcsSyncService syncService = new IcsSyncService(feedRepository, eventRepository, config);
        PruneService pruneService = new PruneService(eventRepository, config);
        FreeTimeService freeTimeService = new FreeTimeService(eventRepository, config);
        SyncScheduler syncScheduler = new SyncScheduler(syncService, pruneService, feedRepository, config);

        // JTE template engine
        TemplateEngine templateEngine = createTemplateEngine();

        // Javalin app
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.fileRenderer(new JavalinJte(templateEngine));
            javalinConfig.showJavalinBanner = false;
        });

        // Register controllers
        new DashboardController(feedRepository, eventRepository, officeLocationRepository, config).register(app);
        new FeedController(feedRepository, syncScheduler).register(app);
        new ApiController(feedRepository, eventRepository, officeLocationRepository, freeTimeService, syncScheduler, config).register(app);

        // MCP server
        McpToolHandler mcpToolHandler = new McpToolHandler(eventRepository, feedRepository, officeLocationRepository, freeTimeService, config);
        McpTransport mcpTransport = new McpTransport(mcpToolHandler);
        app.sse("/mcp/sse", mcpTransport::handleSse);
        app.post("/mcp/messages", mcpTransport::handleMessage);

        // Start sync scheduler
        syncScheduler.start();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            syncScheduler.stop();
            app.stop();
            dataSource.close();
        }));

        int port = config.getServerPort();
        app.start(port);
        log.info("EventStream started on port {}", port);
    }

    private static TemplateEngine createTemplateEngine() {
        // Look for templates: src/main/jte (dev) or jte/ (deployed)
        Path templateDir = Path.of("src/main/jte");
        if (!templateDir.toFile().exists()) {
            templateDir = Path.of("jte");
        }
        if (templateDir.toFile().exists()) {
            CodeResolver codeResolver = new DirectoryCodeResolver(templateDir);
            Path classDir = Path.of("jte-classes");
            classDir.toFile().mkdirs();
            return TemplateEngine.create(codeResolver, classDir, ContentType.Html, Thread.currentThread().getContextClassLoader());
        }
        // In production, use precompiled templates
        return TemplateEngine.createPrecompiled(ContentType.Html);
    }
}

package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository.AuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FurnidataAuditLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataAuditLog.class);

    private FurnidataAuditLog() {}

    public static void record(
            int userId,
            String classname,
            String action,
            String oldName,
            String newName,
            String oldDesc,
            String newDesc) {
        try {
            new FurniEditorRepository(Emulator.getDatabase().getDataSource())
                    .recordAudit(new AuditEntry(
                            userId,
                            classname,
                            action,
                            oldName,
                            newName,
                            oldDesc,
                            newDesc,
                            Emulator.getIntUnixTimestamp()));
        } catch (Exception e) {
            LOGGER.error("Failed to write furnidata_edit_log", e);
        }
    }
}

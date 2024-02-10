package me.hsgamer.bettergui.betterforms.modal;

import me.hsgamer.bettergui.action.ActionApplier;
import me.hsgamer.bettergui.api.menu.StandardMenu;
import me.hsgamer.bettergui.util.ProcessApplierConstants;
import me.hsgamer.bettergui.util.StringReplacerApplier;
import me.hsgamer.hscore.bukkit.scheduler.Scheduler;
import me.hsgamer.hscore.collections.map.CaseInsensitiveStringMap;
import me.hsgamer.hscore.common.MapUtils;
import me.hsgamer.hscore.config.CaseInsensitivePathString;
import me.hsgamer.hscore.config.Config;
import me.hsgamer.hscore.config.PathString;
import me.hsgamer.hscore.task.BatchRunnable;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;
import java.util.function.BiConsumer;

public class ModalFormMenu extends StandardMenu {
    private final String title;
    private final String content;
    private final Map<String, ButtonComponent> buttonComponentMap = new LinkedHashMap<>();
    private final List<BiConsumer<UUID, ModalForm.Builder>> formModifiers = new ArrayList<>();

    public ModalFormMenu(Config config) {
        super(config);

        title = Optional.ofNullable(MapUtils.getIfFound(menuSettings, "title"))
                .map(Object::toString)
                .orElse("");
        content = Optional.ofNullable(MapUtils.getIfFound(menuSettings, "content"))
                .map(Object::toString)
                .orElse("");
        Optional.ofNullable(MapUtils.getIfFound(menuSettings, "close-action"))
                .map(o -> new ActionApplier(this, o))
                .ifPresent(closeAction -> {
                    formModifiers.add((uuid, builder) -> {
                        builder.closedResultHandler(() -> {
                            BatchRunnable batchRunnable = new BatchRunnable();
                            batchRunnable.getTaskPool(ProcessApplierConstants.ACTION_STAGE).addLast(process -> closeAction.accept(uuid, process));
                            Scheduler.current().async().runTask(batchRunnable);
                        });
                    });
                });
        Optional.ofNullable(MapUtils.getIfFound(menuSettings, "invalid-action"))
                .map(o -> new ActionApplier(this, o))
                .ifPresent(invalidAction -> {
                    formModifiers.add((uuid, builder) -> {
                        builder.invalidResultHandler(() -> {
                            BatchRunnable batchRunnable = new BatchRunnable();
                            batchRunnable.getTaskPool(ProcessApplierConstants.ACTION_STAGE).addLast(process -> invalidAction.accept(uuid, process));
                            Scheduler.current().async().runTask(batchRunnable);
                        });
                    });
                });

        for (Map.Entry<CaseInsensitivePathString, Object> configEntry : configSettings.entrySet()) {
            String key = PathString.toPath(configEntry.getKey().getPathString());
            Map<String, Object> value = MapUtils.castOptionalStringObjectMap(configEntry.getValue())
                    .<Map<String, Object>>map(CaseInsensitiveStringMap::new)
                    .orElseGet(Collections::emptyMap);
            buttonComponentMap.put(key, new ButtonComponent(this, key, value));
        }
    }

    @Override
    public boolean create(Player player, String[] args, boolean bypass) {
        UUID uuid = player.getUniqueId();
        if (!FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
            return false;
        }

        ModalForm.Builder builder = ModalForm.builder();
        builder.title(StringReplacerApplier.replace(title, uuid, this));
        builder.content(StringReplacerApplier.replace(content, uuid, this));
        buttonComponentMap.values().forEach(component -> component.apply(uuid, builder));
        formModifiers.forEach(modifier -> modifier.accept(uuid, builder));
        builder.validResultHandler(response -> buttonComponentMap.values().forEach(component -> component.handle(uuid, response)));

        return FloodgateApi.getInstance().getPlayer(uuid).sendForm(builder);
    }

    @Override
    public void update(Player player) {
        // EMPTY
    }

    @Override
    public void close(Player player) {
        // EMPTY
    }

    @Override
    public void closeAll() {
        // EMPTY
    }
}

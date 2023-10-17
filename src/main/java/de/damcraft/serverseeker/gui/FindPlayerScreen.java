package de.damcraft.serverseeker.gui;

import com.google.common.net.HostAndPort;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.damcraft.serverseeker.ServerSeekerSystem;
import de.damcraft.serverseeker.SmallHttp;
import de.damcraft.serverseeker.ssapi_responses.WhereisResponse;
import de.damcraft.serverseeker.utils.MultiplayerScreenUtil;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.Systems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

public class FindPlayerScreen extends WindowScreen {
    private final MultiplayerScreen multiplayerScreen;

    public enum NameOrUUID {
        Name,
        UUID
    }

    private final Settings settings = new Settings();
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<NameOrUUID> nameOrUUID = sg.add(new EnumSetting.Builder<NameOrUUID>()
        .name("name-or-uuid")
        .description("Whether to search by name or UUID.")
        .defaultValue(NameOrUUID.Name)
        .build()
    );

    private final Setting<String> name = sg.add(new StringSetting.Builder()
        .name("name")
        .description("The name to search for.")
        .defaultValue("")
        .visible(() -> nameOrUUID.get() == NameOrUUID.Name)
        .build()
    );

    private final Setting<String> uuid = sg.add(new StringSetting.Builder()
        .name("UUID")
        .description("The UUID to search for.")
        .defaultValue("")
        .visible(() -> nameOrUUID.get() == NameOrUUID.UUID)
        .build()
    );

    WContainer settingsContainer;

    public FindPlayerScreen(MultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "Find Players");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        WContainer settingsContainer = add(theme.verticalList()).minWidth(256 * 1.5).widget();
        settingsContainer.add(theme.settings(settings)).expandX();

        this.settingsContainer = settingsContainer;

        add(theme.button("Find Player")).expandX().widget().action = () -> {
            String apiKey = Systems.get(ServerSeekerSystem.class).apiKey;

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("api_key", apiKey);

            if (nameOrUUID.get() == NameOrUUID.Name) {
                jsonObject.addProperty("name", name.get());
            } else {
                jsonObject.addProperty("uuid", uuid.get());
            }

            String json = jsonObject.toString();

            String jsonResp = SmallHttp.post("https://api.serverseeker.net/whereis", json);

            Gson gson = new Gson();
            WhereisResponse resp = gson.fromJson(jsonResp, WhereisResponse.class);

            // Set error message if there is one
            if (resp.isError()) {
                clear();
                add(theme.label(resp.error)).expandX();
                return;
            }
            clear();

            List<WhereisResponse.Record> data = resp.data;
            if (data.size() == 0) {
                clear();
                add(theme.label("Not found")).expandX();
                return;
            }
            add(theme.label("Found " + data.size() + " servers:"));
            WTable table = add(theme.table()).widget();

            table.add(theme.label("Server IP"));
            table.add(theme.label("Player name"));
            table.add(theme.label("Last seen"));

            WButton addAllButton = table.add(theme.button("Add all")).expandCellX().widget();
            addAllButton.action = () -> addAllServers(data);

            table.row();

            table.add(theme.horizontalSeparator()).expandX();
            table.row();


            for (WhereisResponse.Record server : data) {
                String serverIP = server.server;
                String playerName = server.name;
                long playerLastSeen = server.last_seen; // Unix timestamp

                // Format last seen to human-readable
                String playerLastSeenFormatted = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                    .format(Instant.ofEpochSecond(playerLastSeen).atZone(ZoneId.systemDefault()).toLocalDateTime());

                table.add(theme.label(serverIP));
                table.add(theme.label(playerName));
                table.add(theme.label(playerLastSeenFormatted));

                WButton addServerButton = theme.button("Add Server");
                addServerButton.action = () -> {
                    ServerInfo info = new ServerInfo("ServerSeeker " + serverIP + " (Player: " + playerName + ")", serverIP, ServerInfo.ServerType.OTHER);
                    MultiplayerScreenUtil.addInfoToServerList(multiplayerScreen, info);
                    addServerButton.visible = false;
                };

                HostAndPort hap = HostAndPort.fromString(serverIP);
                WButton joinServerButton = theme.button("Join Server");
                joinServerButton.action = () -> {
                    ConnectScreen.connect(new TitleScreen(), MinecraftClient.getInstance(), new ServerAddress(hap.getHost(), hap.getPort()), new ServerInfo("a", hap.toString(), ServerInfo.ServerType.OTHER), false);
                };

                WButton serverInfoButton = theme.button("Server Info");
                serverInfoButton.action = () -> this.client.setScreen(new ServerInfoScreen(serverIP));

                table.add(addServerButton);
                table.add(joinServerButton);
                table.add(serverInfoButton);
            }
        };
    }

    private void addAllServers(List<WhereisResponse.Record> records) {
        for (WhereisResponse.Record record : records) {
            String serverIP = record.server;
            String playerName = record.name;
            ServerInfo info = new ServerInfo("ServerSeeker " + serverIP + " (Player: " + playerName + ")", serverIP, ServerInfo.ServerType.OTHER);
            MultiplayerScreenUtil.addInfoToServerList(multiplayerScreen, info, false);
        }
        MultiplayerScreenUtil.saveList(multiplayerScreen);
        if (client == null) return;
        client.setScreen(this.multiplayerScreen);
    }

    @Override
    public void tick() {
        super.tick();
        settings.tick(settingsContainer, theme);
    }
}

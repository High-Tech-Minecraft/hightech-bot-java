package com.example.discordbot;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import nl.vv32.rcon.Rcon;

import java.awt.*;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.ROLE;

public class Bot extends ListenerAdapter {

    public static void main(String[] arguments) throws IOException, InterruptedException, SQLException {
        Dotenv dotenv = Dotenv.configure().load();
        JDA jda = JDABuilder.createDefault(dotenv.get("BOT_TOKEN"), GatewayIntent.GUILD_MEMBERS, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_WEBHOOKS, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(new Bot())
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .build().awaitReady();

        Guild guildy = jda.getGuildById(Long.parseLong(dotenv.get("GUILD_ID")));
        Role member = guildy.getRoleById(Long.parseLong(dotenv.get("MEMBER_ROLE_ID")));
        guildy.loadMembers();
        CommandListUpdateAction commands = jda.updateCommands();
        commands.addCommands(
                Commands.slash("santa", "Generates Secret Santa Partners and dms them")
                        .addOptions(new OptionData(ROLE, "role", "The role of those participating")
                        .setRequired(true))
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        );
        commands.addCommands(
                Commands.slash("online","List online players for smp")
                        .setGuildOnly(true)
        );
        commands.addCommands(
                Commands.slash("todo", "sends todo list")
                .setGuildOnly(true)
        );
        commands.queue();
    }
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        if (event.getGuild() == null)
            return;
        switch (event.getName())
        {
            case "santa":
                System.out.println("santa command called");
                Role role = event.getOption("role").getAsRole(); // the "user" option is required, so it doesn't need a null-check here
                santa(event, role);
                break;
            case "online":
                System.out.println("online command called");
                try {
                    online(event);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case "todo":
                System.out.println("todo command called");
                try {
                    todo(event);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                break;
    }

}
    public void todo (SlashCommandInteractionEvent event) throws SQLException {
        Color Purple = new Color(59, 0 ,64);
        Color Dark_Purple = new Color(80, 1, 46);
        InteractionHook hook = event.getHook();
        ArrayList<String> sTodo = new ArrayList<>();
        ArrayList<String> cTodo = new ArrayList<>();
        EmbedBuilder sList = new EmbedBuilder();
        EmbedBuilder cList = new EmbedBuilder();
        sList.setTitle("Survival Todo List");
        cList.setTitle("Creative Todo List");
        sList.setColor(Purple);
        cList.setColor(Dark_Purple);
        sList.setTimestamp(event.getTimeCreated());
        cList.setTimestamp(event.getTimeCreated());
        Dotenv dotenv = Dotenv.configure().load();
        String url= dotenv.get("DATABASE_URL");
        Connection con= DriverManager.getConnection(url);
        Statement st = con.createStatement();
        Statement st2 = con.createStatement();
        ResultSet rt = st.executeQuery("SELECT type FROM myschema.\"todos\"");
        ResultSet tt = st2.executeQuery("SELECT title FROM myschema.\"todos\"");
        event.deferReply(true).queue();
        while (rt.next()){
                if (rt.getString(1).equals("survival")) {
                    tt.next();
                    sTodo.add(tt.getString("title"));
                } else if (rt.getString(1).equals("creative")) {
                    tt.next();
                    cTodo.add(tt.getString("title"));
                } else {
                    System.err.println(rt.getString(1) + " is not a real server type");
                }
            System.out.println(rt.getString(1));
        }
        rt.close();
        st.close();
        StringBuilder sListDescription = new StringBuilder();
        StringBuilder cListDescription = new StringBuilder();

        for (String todo : sTodo) {
            sListDescription.append("• ").append(todo).append("\n");
        }

        for (String todo : cTodo) {
            cListDescription.append("• ").append(todo).append("\n");
        }
        sList.setDescription(sListDescription.toString());
        cList.setDescription(cListDescription.toString());
        MessageEmbed i= sList.build();
        MessageEmbed c= cList.build();
        hook.sendMessageEmbeds(i,c).queue();
    }
    public void online(SlashCommandInteractionEvent event) throws IOException {
        Dotenv dotenv = Dotenv.configure().load();
        event.deferReply(false).queue();
        Guild o=event.getGuild();
        Role g=o.getRoleById(Long.parseLong(dotenv.get("MEMBER_ROLE_ID")));
        InteractionHook hook = event.getHook();
        Rcon rcon = Rcon.open(dotenv.get("SERVER_IP"), Integer.parseInt(dotenv.get("PORT")));
        {
            if (rcon.authenticate(dotenv.get("RCON_PASSWORD"))&&event.getMember().getRoles().contains(g)) {
                hook.sendMessage(rcon.sendCommand("list").toString()).queue();
                rcon.close();
            } else {
                System.out.println("Failed to authenticate");
            }
        }
    }
    public void santa(SlashCommandInteractionEvent event,Role role) {
        Dotenv dotenv = Dotenv.configure().load();
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();
         if(!event.getMember().hasPermission(Permission.ADMINISTRATOR)){
         System.out.println("santa command not sent by admin");
         hook.sendMessage("You don't have the required permissions to run that command.").queue();
         return;
         }
        ArrayList<String> memList=new ArrayList<String>();
        Guild guild = event.getGuild();
        assert guild != null;
        Member admin=guild.getMemberById(Long.parseLong(dotenv.get("ADMIN_ID")));
        List<Member> mems = guild.getMembersWithRoles(role);
        for (Member j:mems){
            memList.add(j.getEffectiveName());
        }
        System.out.println("Gifter, Receiver");
       for (Member i:mems) {
            double j= Math.random()*memList.size();
            while (memList.get((int) j)==i.getEffectiveName()){
                j= Math.random()*memList.size();
            }
            String mess="Make your thing for "+memList.get((int) j)+(" dm admin with any questions!");
            String foradmin=i.getEffectiveName()+", "+memList.get((int) j);
            i.getUser().openPrivateChannel()
                    .flatMap(channel -> channel.sendMessage(mess))
                    .queue();
            admin.getUser().openPrivateChannel()
                    .flatMap((channel -> channel.sendMessage(foradmin)))
                    .queue();
            memList.remove((int)j);
        }
        hook.sendMessage("Success!").queue();
    }
}

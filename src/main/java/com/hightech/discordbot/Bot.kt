package com.hightech.discordbot

import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.MemberCachePolicy
import nl.vv32.rcon.Rcon
import java.awt.Color
import java.io.IOException
import java.sql.DriverManager
import java.sql.SQLException
import java.util.function.Function

class Bot : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild == null) return
        when (event.name) {
            "santa" -> {
                println("santa command called")
                val role =
                    event.getOption("role")!!.asRole // the "user" option is required, so it doesn't need a null-check here
                santa(event, role)
            }

            "online" -> {
                println("online command called")
                try {
                    online(event)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }

            "todo" -> {
                println("todo command called")
                try {
                    todo(event)
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
            }
        }
    }

    @Throws(SQLException::class)
    fun todo(event: SlashCommandInteractionEvent) {
        val Purple = Color(59, 0, 64)
        val Dark_Purple = Color(80, 1, 46)
        val hook = event.hook
        val sTodo = ArrayList<String>()
        val cTodo = ArrayList<String>()
        val sList = EmbedBuilder()
        val cList = EmbedBuilder()
        sList.setTitle("Survival Todo List")
        cList.setTitle("Creative Todo List")
        sList.setColor(Purple)
        cList.setColor(Dark_Purple)
        sList.setTimestamp(event.timeCreated)
        cList.setTimestamp(event.timeCreated)
        val dotenv = Dotenv.configure().load()
        val url = dotenv["DATABASE_URL"]
        val con = DriverManager.getConnection(url)
        val st = con.createStatement()
        val st2 = con.createStatement()
        val rt = st.executeQuery("SELECT type FROM myschema.\"todos\"")
        val tt = st2.executeQuery("SELECT title FROM myschema.\"todos\"")
        event.deferReply(true).queue()
        while (rt.next()) {
            if (rt.getString(1) == "survival") {
                tt.next()
                sTodo.add(tt.getString("title"))
            } else if (rt.getString(1) == "creative") {
                tt.next()
                cTodo.add(tt.getString("title"))
            } else {
                System.err.println(rt.getString(1) + " is not a real server type")
            }
            println(rt.getString(1))
        }
        rt.close()
        st.close()
        val sListDescription = StringBuilder()
        val cListDescription = StringBuilder()

        for (todo in sTodo) {
            sListDescription.append("• ").append(todo).append("\n")
        }

        for (todo in cTodo) {
            cListDescription.append("• ").append(todo).append("\n")
        }
        sList.setDescription(sListDescription.toString())
        cList.setDescription(cListDescription.toString())
        val i = sList.build()
        val c = cList.build()
        hook.sendMessageEmbeds(i, c).queue()
    }

    @Throws(IOException::class)
    fun online(event: SlashCommandInteractionEvent) {
        val dotenv = Dotenv.configure().load()
        event.deferReply(false).queue()
        val o = event.guild
        val g = o!!.getRoleById(dotenv["MEMBER_ROLE_ID"].toLong())
        val hook = event.hook
        val rcon = Rcon.open(dotenv["SERVER_IP"], dotenv["PORT"].toInt())
        run {
            if (rcon.authenticate(dotenv["RCON_PASSWORD"]) && event.member!!.roles.contains(g)) {
                hook.sendMessage(rcon.sendCommand("list").toString()).queue()
                rcon.close()
            } else {
                println("Failed to authenticate")
            }
        }
    }

    fun santa(event: SlashCommandInteractionEvent, role: Role) {
        val dotenv = Dotenv.configure().load()
        event.deferReply(true).queue()
        val hook = event.hook
        if (!event.member!!.hasPermission(Permission.ADMINISTRATOR)) {
            println("santa command not sent by admin")
            hook.sendMessage("You don't have the required permissions to run that command.").queue()
            return
        }
        val memList = ArrayList<String>()
        val guild = checkNotNull(event.guild)
        val admin = guild.getMemberById(dotenv["ADMIN_ID"].toLong())
        val mems = guild.getMembersWithRoles(role)
        for (j in mems) {
            memList.add(j.effectiveName)
        }
        println("Gifter, Receiver")
        for (i in mems) {
            var j = Math.random() * memList.size
            while (memList[j.toInt()] === i.effectiveName) {
                j = Math.random() * memList.size
            }
            val mess = "Make your thing for " + memList[j.toInt()] + (" dm admin with any questions!")
            val foradmin = i.effectiveName + ", " + memList[j.toInt()]
            i.user.openPrivateChannel()
                .flatMap { channel: PrivateChannel -> channel.sendMessage(mess) }
                .queue()
            admin!!.user.openPrivateChannel()
                .flatMap((Function<PrivateChannel, RestAction<Message>> { channel: PrivateChannel ->
                    channel.sendMessage(
                        foradmin
                    )
                }))
                .queue()
            memList.removeAt(j.toInt())
        }
        hook.sendMessage("Success!").queue()
    }

    companion object {
        @Throws(IOException::class, InterruptedException::class, SQLException::class)
        @JvmStatic
        fun main(arguments: Array<String>) {
            val dotenv = Dotenv.configure().load()
            val jda = JDABuilder.createDefault(
                dotenv["BOT_TOKEN"],
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.GUILD_WEBHOOKS,
                GatewayIntent.GUILD_MESSAGES
            )
                .addEventListeners(Bot())
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .build().awaitReady()

            val guildy = jda.getGuildById(dotenv["GUILD_ID"].toLong())
            val member = guildy!!.getRoleById(dotenv["MEMBER_ROLE_ID"].toLong())
            guildy.loadMembers()
            val commands = jda.updateCommands()
            commands.addCommands(
                Commands.slash("santa", "Generates Secret Santa Partners and dms them")
                    .addOptions(
                        OptionData(OptionType.ROLE, "role", "The role of those participating")
                            .setRequired(true)
                    )
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
            )
            commands.addCommands(
                Commands.slash("online", "List online players for smp")
                    .setGuildOnly(true)
            )
            commands.addCommands(
                Commands.slash("todo", "sends todo list")
                    .setGuildOnly(true)
            )
            commands.queue()
        }
    }
}

package com.hightech.discordbot

import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import nl.vv32.rcon.Rcon
import java.awt.Color
import java.io.IOException
import java.sql.DriverManager
import java.sql.SQLException

class Bot : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild == null) return

        when (event.name) {
            "santa" -> {
                println("santa command called")
                val role = event.getOption("role")!!.asRole
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
        val purple = Color(59, 0, 64)
        val darkPurple = Color(80, 1, 46)
        val hook = event.hook
        val survivalTodos = ArrayList<String>()
        val creativeTodos = ArrayList<String>()
        val survivalList = EmbedBuilder()
        val creativeList = EmbedBuilder()

        survivalList.setTitle("Survival Todo List")
        creativeList.setTitle("Creative Todo List")
        survivalList.setColor(purple)
        creativeList.setColor(darkPurple)
        survivalList.setTimestamp(event.timeCreated)
        creativeList.setTimestamp(event.timeCreated)

        val dotenv = Dotenv.configure().load()
        val url = dotenv["DATABASE_URL"]
        val connection = DriverManager.getConnection(url)
        val statement = connection.createStatement()
        val statement2 = connection.createStatement()
        val typeResult = statement.executeQuery("SELECT type FROM myschema.\"todos\"")
        val titleResult = statement2.executeQuery("SELECT title FROM myschema.\"todos\"")

        event.deferReply(true).queue()

        while (typeResult.next()) {
            when (typeResult.getString(1)) {
                "survival" -> {
                    titleResult.next()
                    survivalTodos.add(titleResult.getString("title"))
                }
                "creative" -> {
                    titleResult.next()
                    creativeTodos.add(titleResult.getString("title"))
                }
                else -> {
                    System.err.println("${typeResult.getString(1)} is not a real server type")
                }
            }
            println(typeResult.getString(1))
        }

        typeResult.close()
        statement.close()

        val survivalDescription = StringBuilder()
        val creativeDescription = StringBuilder()

        for (todo in survivalTodos) {
            survivalDescription.append("• ").append(todo).append("\n")
        }

        for (todo in creativeTodos) {
            creativeDescription.append("• ").append(todo).append("\n")
        }

        survivalList.setDescription(survivalDescription.toString())
        creativeList.setDescription(creativeDescription.toString())

        val survivalEmbed = survivalList.build()
        val creativeEmbed = creativeList.build()
        hook.sendMessageEmbeds(survivalEmbed, creativeEmbed).queue()
    }

    @Throws(IOException::class)
    fun online(event: SlashCommandInteractionEvent) {
        val dotenv = Dotenv.configure().load()
        event.deferReply(false).queue()
        val guild = event.guild
        val memberRole = guild!!.getRoleById(dotenv["MEMBER_ROLE_ID"].toLong())
        val hook = event.hook
        val rcon = Rcon.open(dotenv["SERVER_IP"], dotenv["PORT"].toInt())

        if (rcon.authenticate(dotenv["RCON_PASSWORD"]) && event.member!!.roles.contains(memberRole)) {
            hook.sendMessage(rcon.sendCommand("list").toString()).queue()
            rcon.close()
        } else {
            println("Failed to authenticate")
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

        val membersList = ArrayList<String>()
        val guild = checkNotNull(event.guild)
        val admin = guild.getMemberById(dotenv["ADMIN_ID"].toLong())
        val members = guild.getMembersWithRoles(role)

        for (member in members) {
            membersList.add(member.effectiveName)
        }

        println("Gifter, Receiver")

        for (member in members) {
            var randomIndex = Math.random() * membersList.size
            while (membersList[randomIndex.toInt()] == member.effectiveName) {
                randomIndex = Math.random() * membersList.size
            }

            val message = "Make your thing for ${membersList[randomIndex.toInt()]} dm admin with any questions!"
            val adminMessage = "${member.effectiveName}, ${membersList[randomIndex.toInt()]}"

            member.user.openPrivateChannel()
                .flatMap { channel -> channel.sendMessage(message) }
                .queue()

            admin!!.user.openPrivateChannel()
                .flatMap { channel -> channel.sendMessage(adminMessage) }
                .queue()

            membersList.removeAt(randomIndex.toInt())
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

            val guild = jda.getGuildById(dotenv["GUILD_ID"].toLong())
            val member = guild!!.getRoleById(dotenv["MEMBER_ROLE_ID"].toLong())
            guild.loadMembers()

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
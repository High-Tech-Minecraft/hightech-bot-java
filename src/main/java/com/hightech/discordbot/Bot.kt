package com.hightech.discordbot

import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
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
import java.util.concurrent.ThreadLocalRandom

class Bot : ListenerAdapter() {
    companion object {
        // Cache Dotenv instance to avoid reloading environment variables
        private val dotenv: Dotenv by lazy { Dotenv.configure().load() }
        
        @Throws(IOException::class, InterruptedException::class, SQLException::class)
        @JvmStatic
        fun main(arguments: Array<String>) {
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

            val guildId = dotenv["GUILD_ID"] ?: throw IllegalStateException("GUILD_ID not found in environment")
            val guild = jda.getGuildById(guildId.toLong()) ?: throw IllegalStateException("Guild not found")
            guild.loadMembers()

            // Update member count channel name on startup
            updateMemberCountChannel(guild)

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

        // Function to update member count channel name
        fun updateMemberCountChannel(guild: Guild) {
            val channelId = dotenv["MEMBER_COUNT_CHANNEL_ID"] ?: return
            val channel = guild.getVoiceChannelById(channelId.toLong()) ?: return
            val newName = "Members: ${guild.memberCount}"
            if (channel.name != newName) {
                channel.manager.setName(newName).reason("Updating member count").queue()
            }
        }
    }

    // Listen for member join event
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val guild = event.guild
        updateMemberCountChannel(guild)
    }

    // Listen for member leave event
    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val guild = event.guild
        updateMemberCountChannel(guild)
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild == null) return

        try {
            when (event.name) {
                "santa" -> {
                    println("santa command called")
                    val role = event.getOption("role")?.asRole 
                        ?: throw IllegalArgumentException("Role option is required")
                    santa(event, role)
                }
                "online" -> {
                    println("online command called")
                    online(event)
                }
                "todo" -> {
                    println("todo command called")
                    todo(event)
                }
            }
        } catch (e: Exception) {
            println("Error handling command ${event.name}: ${e.message}")
            event.hook.sendMessage("An error occurred while processing your command. Please try again later.").queue()
        }
    }

    // Listen for messages in the application channel to create polls
    override fun onMessageReceived(event: MessageReceivedEvent) {
        val appChannelId = dotenv["APPLICATION_CHANNEL_ID"] ?: return
        val botId = event.jda.selfUser.id
        if (event.channel.id != appChannelId) return
        if (event.author.id == botId) return

        // Start a thread for the application
        event.message.createThreadChannel("Application")
            .queue { thread ->
                val poll = net.dv8tion.jda.api.utils.messages.MessagePollData.builder("Accept?")
                    .addAnswer("Accept", Emoji.fromUnicode("✅"))
                    .addAnswer("Deny", Emoji.fromUnicode("❌"))
                    .build()
                event.channel.sendMessage("Vote").setPoll(poll).queue()
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

        val url = dotenv["DATABASE_URL"] ?: throw IllegalStateException("DATABASE_URL not found in environment")

        event.deferReply(true).queue()

        // Use a single query to fetch both type and title at once
        // Use try-with-resources equivalent in Kotlin to ensure proper resource cleanup
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                // Single query to get both columns at once
                val result = statement.executeQuery("SELECT type, title FROM myschema.\"todos\"")

                // Process the results
                while (result.next()) {
                    val type = result.getString("type")
                    val title = result.getString("title")

                    when (type) {
                        "survival" -> survivalTodos.add(title)
                        "creative" -> creativeTodos.add(title)
                        else -> System.err.println("$type is not a real server type")
                    }
                    println(type)
                }
            }
        }

        val survivalDescription = StringBuilder()
        val creativeDescription = StringBuilder()

        // Use joinToString for better performance and readability
        if (survivalTodos.isNotEmpty()) {
            survivalDescription.append(survivalTodos.joinToString("\n") { "• $it" })
        }

        if (creativeTodos.isNotEmpty()) {
            creativeDescription.append(creativeTodos.joinToString("\n") { "• $it" })
        }

        survivalList.setDescription(survivalDescription.toString())
        creativeList.setDescription(creativeDescription.toString())

        val survivalEmbed = survivalList.build()
        val creativeEmbed = creativeList.build()
        hook.sendMessageEmbeds(survivalEmbed, creativeEmbed).queue()
    }

    @Throws(IOException::class)
    fun online(event: SlashCommandInteractionEvent) {
        event.deferReply(false).queue()
        val guild = event.guild ?: throw IllegalStateException("Command must be used in a guild")
        val memberRoleId = dotenv["MEMBER_ROLE_ID"] ?: throw IllegalStateException("MEMBER_ROLE_ID not found in environment")
        val memberRole = guild.getRoleById(memberRoleId.toLong())
        val hook = event.hook
        
        val serverIp = dotenv["SERVER_IP"] ?: throw IllegalStateException("SERVER_IP not found in environment")
        val port = dotenv["PORT"]?.toIntOrNull() ?: throw IllegalStateException("PORT not found or invalid in environment")
        val rconPassword = dotenv["RCON_PASSWORD"] ?: throw IllegalStateException("RCON_PASSWORD not found in environment")
        
        var rcon: Rcon? = null
        try {
            rcon = Rcon.open(serverIp, port)
            val member = event.member ?: throw IllegalStateException("Member information not available")
            
            if (rcon.authenticate(rconPassword) && member.roles.contains(memberRole)) {
                hook.sendMessage(rcon.sendCommand("list").toString()).queue()
            } else {
                println("Failed to authenticate or member doesn't have required role")
                hook.sendMessage("You don't have permission to use this command.").queue()
            }
        } finally {
            rcon?.close()
        }
    }

    fun santa(event: SlashCommandInteractionEvent, role: Role) {
        event.deferReply(true).queue()
        val hook = event.hook

        val member = event.member ?: throw IllegalStateException("Member information not available")
        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            println("santa command not sent by admin")
            hook.sendMessage("You don't have the required permissions to run that command.").queue()
            return
        }

        val guild = event.guild ?: throw IllegalStateException("Command must be used in a guild")
        val adminId = dotenv["ADMIN_ID"] ?: throw IllegalStateException("ADMIN_ID not found in environment")
        val admin = guild.getMemberById(adminId.toLong())
        val members = guild.getMembersWithRoles(role)

        // Check if we have enough members for Secret Santa
        if (members.size < 2) {
            hook.sendMessage("Need at least 2 members to run Secret Santa!").queue()
            return
        }

        val membersList = members.map { it.effectiveName }.toMutableList()

        println("Gifter, Receiver")

        for (member in members) {
            // Remove current member from available targets to prevent self-assignment
            val availableTargets = membersList.filter { it != member.effectiveName }
            
            if (availableTargets.isEmpty()) {
                // This should not happen with proper logic, but handle gracefully
                hook.sendMessage("Error: Unable to assign Secret Santa partners. Please try again.").queue()
                return
            }
            
            // Use ThreadLocalRandom for better performance and thread safety
            val randomIndex = ThreadLocalRandom.current().nextInt(availableTargets.size)
            val target = availableTargets[randomIndex]
            
            val message = "Make your thing for $target dm admin with any questions!"
            val adminMessage = "${member.effectiveName}, $target"

            member.user.openPrivateChannel()
                .flatMap { channel -> channel.sendMessage(message) }
                .queue()

            admin?.user?.openPrivateChannel()
                ?.flatMap { channel -> channel.sendMessage(adminMessage) }
                ?.queue()

            // Remove the assigned target from the list
            membersList.remove(target)
        }

        hook.sendMessage("Success!").queue()
    }
}
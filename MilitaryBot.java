import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.awt.Color;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

public class MilitaryBot extends ListenerAdapter {

    // --- CONFIGURATION VARIABLES ---
    private static final String BOT_TOKEN = System.getenv("DISCORD_TOKEN");

    // Role IDs for permissions
    private static final long BASIC_COMMAND_ROLE_ID = 1415166608921591861L; // Basic command access
    private static final long ADMIN_COMMAND_ROLE_ID = 1348877561257791539L; // Admin command access (promotions, infractions, activity)

    // Channel IDs where embeds should be sent
    private static final long PROMOTION_CHANNEL_ID = 1348787021723734076L;
    private static final long DEMOTION_CHANNEL_ID = 1348787047300599939L;
    private static final long INFRACTION_CHANNEL_ID = 1348787047300599939L;
    private static final long DEPLOYMENT_CHANNEL_ID = 1360030381147160771L;
    private static final long ACTIVITY_CHANNEL_ID = 1361539341754699876L;
    private static final long LOG_CHANNEL_ID = 1415167671044935841L; // Command logging channel

    // URL for the deployment vote image
    private static final String DEPLOYMENT_IMAGE_URL = "https://media.discordapp.net/attachments/1361550335449104436/1404327019910791238/image.png?ex=68c1ad34&is=68c05bb4&hm=95f0707a927a6b2c3f7d163cf25a949d5ec973be7000ee65c1ef3902f2c0da18&=&format=webp&quality=lossless";

    // Active deployment tracking
    private static final Map<Long, DeploymentData> activeDeployments = new ConcurrentHashMap<>();
    
    // Deployment data class
    private static class DeploymentData {
        final int requiredParticipants;
        final String startupInfo;
        final Set<Long> participants;
        boolean started;
        
        DeploymentData(int required, String info) {
            this.requiredParticipants = required;
            this.startupInfo = info;
            this.participants = new HashSet<>();
            this.started = false;
        }
    }

    // Professional color scheme for embeds
    private static final int COLOR_SUCCESS = 0x57F287; // Green
    private static final int COLOR_WARNING = 0xFEE75C; // Yellow
    private static final int COLOR_ERROR = 0xED4245; // Red
    private static final int COLOR_INFO = 0x5865F2; // Discord Blurple
    private static final int COLOR_PROMOTION = 0x00FF7F; // Spring Green
    private static final int COLOR_INFRACTION = 0xFF4500; // Orange Red
    private static final int COLOR_DEPLOYMENT = 0x1E90FF; // Dodge Blue
    private static final int COLOR_ACTIVITY = 0xFFD700; // Gold
    private static final int COLOR_CLASSIFIED = 0x2C5AA0; // Navy Blue

    public static void main(String[] args) throws Exception {
        if (BOT_TOKEN == null) {
            System.err.println("DISCORD_TOKEN environment variable not set. Please set it up in Replit secrets.");
            return;
        }

        JDABuilder builder = JDABuilder.createDefault(BOT_TOKEN, EnumSet.allOf(GatewayIntent.class));
        builder.addEventListeners(new MilitaryBot());

        JDA jda = builder.build();
        jda.awaitReady();
        System.out.println("🚀 Military Bot is operational!");
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        Guild guild = event.getGuild();
        
        // Register slash commands
        guild.updateCommands().addCommands(
            // Admin commands
            Commands.slash("promote", "Promote a user to a new rank")
                .addOption(OptionType.USER, "user", "The user to promote", true)
                .addOption(OptionType.ROLE, "role", "The role to assign", true)
                .addOption(OptionType.STRING, "reason", "Reason for promotion", true),
            
            Commands.slash("infraction", "Issue an infraction to a staff member")
                .addOption(OptionType.USER, "user", "The staff member", true)
                .addOptions(new OptionData(OptionType.STRING, "action", "Type of disciplinary action", true)
                    .addChoice("Verbal Warning", "Verbal Warning")
                    .addChoice("Warning", "Warning")
                    .addChoice("Strike", "Strike")
                    .addChoice("Termination", "Termination")
                    .addChoice("Demotion", "Demotion"))
                .addOption(OptionType.STRING, "reason", "Reason for infraction", true)
                .addOption(OptionType.ROLE, "demoted_to", "New role if demoted (optional for demotion)", false),
            
            Commands.slash("activitycheck", "Start an activity check")
                .addOption(OptionType.INTEGER, "goal", "Target number of reactions", true),
                
            Commands.slash("modpanel", "Open moderation panel (Admin only)")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOptions(new OptionData(OptionType.STRING, "action", "Moderation action", true)
                    .addChoice("Timeout 5m", "timeout_5m")
                    .addChoice("Timeout 1h", "timeout_1h")
                    .addChoice("Timeout 1d", "timeout_1d")
                    .addChoice("Remove Timeout", "remove_timeout")
                    .addChoice("Kick", "kick")
                    .addChoice("Remove Role", "remove_role")
                    .addChoice("Give Role", "give_role"))
                .addOption(OptionType.ROLE, "role", "Role for role actions (optional)", false)
                .addOption(OptionType.STRING, "reason", "Reason for action", false),
            
            // General commands
            Commands.slash("deploy", "Create a deployment vote")
                .addOption(OptionType.INTEGER, "required", "Required participants to start", true)
                .addOption(OptionType.STRING, "startup_info", "Info to send when deployment starts", true)
                .addOption(OptionType.STRING, "message", "Additional deployment message (optional)", false),
            
            Commands.slash("nickname", "Change your nickname")
                .addOption(OptionType.STRING, "name", "Your new nickname", true),
                
            Commands.slash("avatar", "Get someone's avatar")
                .addOption(OptionType.USER, "user", "User to get avatar from (optional)", false),
                
            Commands.slash("userinfo", "Get user information")
                .addOption(OptionType.USER, "user", "User to get info about (optional)", false),
                
            Commands.slash("help", "Get command information and usage guide")
        ).queue();
        
        System.out.println("✅ Slash commands registered for guild: " + guild.getName());
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Ignore bot reactions
        if (event.getUser().isBot()) return;
        
        // Check for activity check reactions
        if (event.getChannel().getIdLong() == ACTIVITY_CHANNEL_ID && 
            event.getReaction().getEmoji().getName().equals("✅")) {
            handleActivityCheckReaction(event);
        }
        
        // Check for deployment reactions
        if (event.getChannel().getIdLong() == DEPLOYMENT_CHANNEL_ID && 
            event.getReaction().getEmoji().getName().equals("✅")) {
            handleDeploymentReaction(event);
        }
    }
    
    private void handleActivityCheckReaction(MessageReactionAddEvent event) {
            
        event.retrieveMessage().queue(message -> {
            if (message.getEmbeds().isEmpty()) return;
            
            var embed = message.getEmbeds().get(0);
            if (embed.getTitle() != null && embed.getTitle().contains("ACTIVITY ASSESSMENT")) {
                // Count unique users who reacted with checkmark
                event.getReaction().retrieveUsers().queue(users -> {
                    int count = (int) users.stream().filter(user -> !user.isBot()).count();
                    
                    // Extract goal from embed
                    String goalField = embed.getFields().stream()
                        .filter(field -> field.getName().contains("TARGET"))
                        .map(field -> field.getValue())
                        .findFirst().orElse("0");
                    
                    String goalStr = goalField.replaceAll("[^0-9]", "");
                    int goal = goalStr.isEmpty() ? 0 : Integer.parseInt(goalStr);
                    
                    // Update the embed with new count
                    EmbedBuilder newEmbed = new EmbedBuilder()
                        .setColor(COLOR_ACTIVITY)
                        .setAuthor("📊 PERSONNEL ACTIVITY CHECK", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
                        .setTitle("**⚡ ACTIVITY ASSESSMENT INITIATED ⚡**")
                        .setDescription("```diff\n+ PERSONNEL VERIFICATION ACTIVE +\n```")
                        .addField("🎯 **TARGET CONFIRMATIONS**", "```\n" + goal + "+ responses required\n```", true)
                        .addField("📈 **CURRENT STATUS**", "```\n" + count + "/" + goal + " confirmed\n```", true)
                        .addField("🔄 **STATUS**", "```\nACTIVE MONITORING\n```", true)
                        .addField("📋 **INSTRUCTIONS**", "```\n• React with ✅ to confirm active status\n• Response helps assess server activity\n• All personnel encouraged to participate\n```", false)
                        .setFooter("PMC Personnel Division • Activity Monitoring", event.getJDA().getSelfUser().getAvatarUrl())
                        .setTimestamp(Instant.now());
                    
                    message.editMessageEmbeds(newEmbed.build()).queue();
                });
            }
        });
    }
    
    private void handleDeploymentReaction(MessageReactionAddEvent event) {
        event.retrieveMessage().queue(message -> {
            if (message.getEmbeds().isEmpty()) return;
            
            var embed = message.getEmbeds().get(0);
            if (embed.getTitle() != null && embed.getTitle().contains("DEPLOYMENT")) {
                long messageId = message.getIdLong();
                DeploymentData deployment = activeDeployments.get(messageId);
                if (deployment == null || deployment.started) return;
                
                // Add participant
                deployment.participants.add(event.getUser().getIdLong());
                
                // Check if we have enough participants
                if (deployment.participants.size() >= deployment.requiredParticipants) {
                    startDeployment(message, deployment);
                }
            }
        });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        // Check basic command permission
        if (!hasRole(member, BASIC_COMMAND_ROLE_ID)) {
            event.reply("🚫 **Access Denied**\nYou don't have permission to use this command.").setEphemeral(true).queue();
            logCommand(event, "ACCESS_DENIED", "Insufficient permissions");
            return;
        }

        String command = event.getName();
        
        // Check admin command permission for specific commands
        if ((command.equals("promote") || command.equals("infraction") || command.equals("activitycheck") || command.equals("modpanel")) 
            && !hasRole(member, ADMIN_COMMAND_ROLE_ID)) {
            event.reply("🚫 **Admin Access Required**\nThis command requires admin permissions.").setEphemeral(true).queue();
            logCommand(event, "ACCESS_DENIED", "Insufficient admin permissions");
            return;
        }

        try {
            switch (command) {
                case "promote":
                    handlePromotion(event);
                    break;
                case "infraction":
                    handleInfraction(event);
                    break;
                case "deploy":
                    handleDeployment(event);
                    break;
                case "activitycheck":
                    handleActivityCheck(event);
                    break;
                case "modpanel":
                    handleModerationPanel(event);
                    break;
                case "nickname":
                    handleNickname(event);
                    break;
                case "avatar":
                    handleAvatar(event);
                    break;
                case "userinfo":
                    handleUserInfo(event);
                    break;
                case "help":
                    handleHelp(event);
                    break;
                default:
                    event.reply("❌ Unknown command.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            System.err.println("Error processing command: " + command);
            e.printStackTrace();
            event.reply("⚠️ **SYSTEM ERROR**\nCommand execution failed. Error has been logged.").setEphemeral(true).queue();
            logCommand(event, "ERROR", "Exception: " + e.getMessage());
        }
    }

    private void handlePromotion(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        Role newRole = event.getOption("role").getAsRole();
        String reason = event.getOption("reason").getAsString();
        
        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }

        // Add role to member
        event.getGuild().addRoleToMember(targetMember, newRole).queue();

        // Create professional promotion embed with classified styling
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(COLOR_PROMOTION)
            .setAuthor("🎖️ PERSONNEL PROMOTION AUTHORIZATION", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("# ⚡ RANK ADVANCEMENT CONFIRMED ⚡")
            .setDescription("```diff\n+ PROMOTION AUTHORIZATION EXECUTED +\n```")
            .addField("👤 **PERSONNEL**", "```\n" + targetMember.getUser().getName() + "\n```", true)
            .addField("🏅 **NEW DESIGNATION**", "```\n" + newRole.getName() + "\n```", true)
            .addField("🎯 **PROMOTION ID**", "```\nPROM-" + generateUniqueId() + "\n```", true)
            .addField("📝 **AUTHORIZATION REASON**", "```\n" + reason + "\n```", false)
            .addField("✍️ **COMMANDING OFFICER**", "```\n" + event.getUser().getName() + "\n```", true)
            .addField("📊 **STATUS**", "```\nAPPROVED & ACTIVE\n```", true)
            .addField("\u200B", "\u200B", true)
            .setThumbnail(targetUser.getAvatarUrl())
            .setFooter("PMC Personnel Division • Promotion Authorization System", event.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());

        TextChannel promoChannel = event.getGuild().getTextChannelById(PROMOTION_CHANNEL_ID);
        if (promoChannel != null) {
            promoChannel.sendMessageEmbeds(embed.build()).queue();
        }

        event.reply("✅ **Promotion Completed**\nUser has been promoted and logged.").setEphemeral(true).queue();
        logCommand(event, "PROMOTION", "User: " + targetUser.getName() + " | Role: " + newRole.getName() + " | Reason: " + reason);
    }

    private void handleInfraction(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String action = event.getOption("action").getAsString();
        String reason = event.getOption("reason").getAsString();
        
        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }

        String demotionInfo = "";
        if (action.equals("Demotion") && event.getOption("demoted_to") != null) {
            Role demotedToRole = event.getOption("demoted_to").getAsRole();
            demotionInfo = "\n**Demoted To:** " + demotedToRole.getAsMention();
            // Apply the demotion role
            event.getGuild().addRoleToMember(targetMember, demotedToRole).queue();
        }

        // Create professional infraction embed with classified styling
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(COLOR_INFRACTION)
            .setAuthor("⚠️ DISCIPLINARY ACTION PROTOCOL", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ PERSONNEL CONSEQUENCE ISSUED ⚡**")
            .setDescription("```diff\n+ DISCIPLINARY RECORD UPDATED +\n```")
            .addField("👤 **SUBJECT PERSONNEL**", "```\n" + targetMember.getUser().getName() + "\n```", true)
            .addField("⚡ **DISCIPLINARY ACTION**", "```\n" + action + "\n```", true)
            .addField("🎯 **CASE REFERENCE**", "```\nINF-" + generateUniqueId() + "\n```", true)
            .addField("📝 **VIOLATION DETAILS**", "```\n" + reason + "\n```", false);

        if (!demotionInfo.isEmpty()) {
            embed.addField("📉 **RANK ADJUSTMENT**", "```\n" + demotionInfo.replace("**Demoted To:** ", "").replace("<@&", "").replace(">", "") + "\n```", false);
        }

        embed.addField("✍️ **ISSUING OFFICER**", "```\n" + event.getUser().getName() + "\n```", true)
            .addField("📊 **ACTION STATUS**", "```\nPROCESSED & FILED\n```", true)
            .addField("\u200B", "\u200B", true)
            .setThumbnail(targetUser.getAvatarUrl())
            .setFooter("PMC Disciplinary Division • Personnel Management System", event.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());

        TextChannel infractionChannel = event.getGuild().getTextChannelById(INFRACTION_CHANNEL_ID);
        if (infractionChannel != null) {
            infractionChannel.sendMessageEmbeds(embed.build()).queue();
        }

        event.reply("✅ **Infraction Recorded**\nDisciplinary action has been logged.").setEphemeral(true).queue();
        logCommand(event, "INFRACTION", "User: " + targetUser.getName() + " | Action: " + action + " | Reason: " + reason + demotionInfo);
    }

    private void handleDeployment(SlashCommandInteractionEvent event) {
        int requiredParticipants = event.getOption("required").getAsInt();
        String startupInfo = event.getOption("startup_info").getAsString();
        String additionalMessage = event.getOption("message") != null ? event.getOption("message").getAsString() : "";

        // Create professional deployment embed with classified styling
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(COLOR_DEPLOYMENT)
            .setAuthor("🚁 OPERATION DEPLOYMENT ALERT", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ TACTICAL DEPLOYMENT AVAILABLE ⚡**")
            .setDescription("```diff\n+ OPERATION AUTHORIZATION PENDING +\n```")
            .addField("🎯 **PARTICIPATION REQUIREMENT**", "```\n" + requiredParticipants + "+ confirmed operators needed\n```", true)
            .addField("📊 **CURRENT STATUS**", "```\n0/" + requiredParticipants + " confirmed\n```", true)
            .addField("🔄 **OPERATION STATUS**", "```\nAWAITING CONFIRMATION\n```", true)
            .addField("📋 **MISSION BRIEFING**", "```\nReact with ✅ to confirm participation\nDeployment auto-starts when quota met\nAll confirmed operators will receive details\n```", false);

        if (!additionalMessage.isEmpty()) {
            embed.addField("📝 **ADDITIONAL INTEL**", "```\n" + additionalMessage + "\n```", false);
        }

        embed.setImage(DEPLOYMENT_IMAGE_URL)
            .setFooter("PMC Operations Division • Tactical Deployment System", event.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());

        TextChannel deploymentChannel = event.getGuild().getTextChannelById(DEPLOYMENT_CHANNEL_ID);
        if (deploymentChannel != null) {
            // Send @everyone ping first, then the embed
            deploymentChannel.sendMessage("@everyone").queue(pingMessage -> {
                deploymentChannel.sendMessageEmbeds(embed.build()).queue(message -> {
                    // Store deployment data
                    activeDeployments.put(message.getIdLong(), new DeploymentData(requiredParticipants, startupInfo));
                    message.addReaction(Emoji.fromUnicode("✅")).queue();
                });
            });
        }

        event.reply("✅ **Deployment Posted**\nDeployment notification has been sent. Requires " + requiredParticipants + " participants to start.").setEphemeral(true).queue();
        logCommand(event, "DEPLOYMENT", "Required: " + requiredParticipants + " | Startup Info: " + startupInfo + " | Message: " + additionalMessage);
    }

    private void handleActivityCheck(SlashCommandInteractionEvent event) {
        int goal = event.getOption("goal").getAsInt();

        // Create professional activity check embed
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(COLOR_ACTIVITY)
            .setAuthor("📊 PERSONNEL ACTIVITY CHECK", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ ACTIVITY ASSESSMENT INITIATED ⚡**")
            .setDescription("```diff\n+ PERSONNEL VERIFICATION ACTIVE +\n```")
            .addField("🎯 **TARGET CONFIRMATIONS**", "```\n" + goal + "+ responses required\n```", true)
            .addField("📈 **CURRENT STATUS**", "```\n0/" + goal + " confirmed\n```", true)
            .addField("🔄 **STATUS**", "```\nACTIVE MONITORING\n```", true)
            .addField("📋 **INSTRUCTIONS**", "```\n• React with ✅ to confirm active status\n• Response helps assess server activity\n• All personnel encouraged to participate\n```", false)
            .setFooter("PMC Personnel Division • Activity Monitoring", event.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());

        TextChannel activityChannel = event.getGuild().getTextChannelById(ACTIVITY_CHANNEL_ID);
        if (activityChannel != null) {
            // Send @everyone ping first, then the embed
            activityChannel.sendMessage("@everyone").queue(pingMessage -> {
                activityChannel.sendMessageEmbeds(embed.build()).queue(message -> {
                    message.addReaction(Emoji.fromUnicode("✅")).queue();
                });
            });
        }

        event.reply("✅ **Activity Check Started**\nActivity check has been posted to the activity channel.").setEphemeral(true).queue();
        logCommand(event, "ACTIVITY_CHECK", "Goal: " + goal);
    }
    
    private void startDeployment(net.dv8tion.jda.api.entities.Message message, DeploymentData deployment) {
        deployment.started = true;
        
        // DM all participants
        for (Long userId : deployment.participants) {
            message.getGuild().retrieveMemberById(userId).queue(member -> {
                member.getUser().openPrivateChannel().queue(channel -> {
                    EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setColor(COLOR_SUCCESS)
                        .setAuthor("🚁 DEPLOYMENT INITIATED", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
                        .setTitle("**⚡ OPERATION COMMENCING ⚡**")
                        .setDescription("```diff\n+ DEPLOYMENT HAS STARTED +\n```")
                        .addField("📝 **MISSION INTEL**", "```\n" + deployment.startupInfo + "\n```", false)
                        .addField("📊 **STATUS**", "```\nACTIVE DEPLOYMENT\n```", true)
                        .addField("🎯 **YOUR ROLE**", "```\nCONFIRMED OPERATOR\n```", true)
                        .setFooter("PMC Operations Division • Mission Briefing", message.getJDA().getSelfUser().getAvatarUrl())
                        .setTimestamp(Instant.now());
                    
                    channel.sendMessageEmbeds(dmEmbed.build()).queue();
                });
            });
        }
        
        // Update channel message
        EmbedBuilder startedEmbed = new EmbedBuilder()
            .setColor(COLOR_SUCCESS)
            .setAuthor("🚁 DEPLOYMENT STATUS UPDATE", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ OPERATION DEPLOYMENT STARTED ⚡**")
            .setDescription("```diff\n+ DEPLOYMENT IS NOW ACTIVE +\n```")
            .addField("👥 **CONFIRMED OPERATORS**", "```\n" + deployment.participants.size() + " personnel deployed\n```", true)
            .addField("📊 **MISSION STATUS**", "```\nACTIVE - IN PROGRESS\n```", true)
            .addField("🔄 **NEXT STEPS**", "```\nALL OPERATORS BRIEFED\n```", true)
            .addField("📝 **STARTUP BRIEFING**", "```\n" + deployment.startupInfo + "\n```", false)
            .setFooter("PMC Operations Division • Active Deployment", message.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());
            
        message.editMessageEmbeds(startedEmbed.build()).queue();
        
        // Clean up deployment data after a delay
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                activeDeployments.remove(message.getIdLong());
            }
        }, 300000); // Remove after 5 minutes
    }
    
    private void handleModerationPanel(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String action = event.getOption("action").getAsString();
        Role role = event.getOption("role") != null ? event.getOption("role").getAsRole() : null;
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";
        
        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }
        
        String actionResult;
        try {
            switch (action) {
                case "timeout_5m":
                    targetMember.timeoutFor(Duration.ofMinutes(5)).queue();
                    actionResult = "5 minute timeout applied";
                    break;
                case "timeout_1h":
                    targetMember.timeoutFor(Duration.ofHours(1)).queue();
                    actionResult = "1 hour timeout applied";
                    break;
                case "timeout_1d":
                    targetMember.timeoutFor(Duration.ofDays(1)).queue();
                    actionResult = "24 hour timeout applied";
                    break;
                case "remove_timeout":
                    targetMember.removeTimeout().queue();
                    actionResult = "Timeout removed";
                    break;
                case "kick":
                    targetMember.kick().reason(reason).queue();
                    actionResult = "User kicked from server";
                    break;
                case "remove_role":
                    if (role == null) {
                        event.reply("❌ Role parameter required for role removal.").setEphemeral(true).queue();
                        return;
                    }
                    event.getGuild().removeRoleFromMember(targetMember, role).queue();
                    actionResult = "Role " + role.getName() + " removed";
                    break;
                case "give_role":
                    if (role == null) {
                        event.reply("❌ Role parameter required for role assignment.").setEphemeral(true).queue();
                        return;
                    }
                    event.getGuild().addRoleToMember(targetMember, role).queue();
                    actionResult = "Role " + role.getName() + " assigned";
                    break;
                default:
                    event.reply("❌ Unknown moderation action.").setEphemeral(true).queue();
                    return;
            }
            
            // Create moderation result embed
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(COLOR_WARNING)
                .setAuthor("🛡️ MODERATION ACTION EXECUTED", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
                .setTitle("**⚡ DISCIPLINARY PROTOCOL APPLIED ⚡**")
                .setDescription("```diff\n+ MODERATION ACTION COMPLETED +\n```")
                .addField("👤 **TARGET USER**", "```\n" + targetUser.getName() + "\n```", true)
                .addField("⚡ **ACTION TAKEN**", "```\n" + actionResult + "\n```", true)
                .addField("🎯 **MOD ID**", "```\nMOD-" + generateUniqueId() + "\n```", true)
                .addField("📝 **JUSTIFICATION**", "```\n" + reason + "\n```", false)
                .addField("✍️ **MODERATOR**", "```\n" + event.getUser().getName() + "\n```", true)
                .addField("📊 **STATUS**", "```\nAPPLIED SUCCESSFULLY\n```", true)
                .setFooter("PMC Security Division • Moderation Panel", event.getJDA().getSelfUser().getAvatarUrl())
                .setTimestamp(Instant.now());
                
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            logCommand(event, "MODERATION", "Target: " + targetUser.getName() + " | Action: " + actionResult + " | Reason: " + reason);
            
        } catch (HierarchyException e) {
            event.reply("❌ Cannot perform action - target user has higher permissions.").setEphemeral(true).queue();
        } catch (InsufficientPermissionException e) {
            event.reply("❌ Insufficient permissions to perform this action.").setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("❌ Failed to execute moderation action: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
    
    private void handleNickname(SlashCommandInteractionEvent event) {
        String nickname = event.getOption("name").getAsString();
        Member member = event.getMember();
        
        if (nickname.length() > 32) {
            event.reply("❌ Nickname too long! Must be 32 characters or less.").setEphemeral(true).queue();
            return;
        }
        
        try {
            member.modifyNickname(nickname).queue(
                success -> {
                    EmbedBuilder embed = new EmbedBuilder()
                        .setColor(COLOR_SUCCESS)
                        .setAuthor("🏷️ CALLSIGN UPDATE", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
                        .setTitle("**⚡ PERSONNEL IDENTIFIER MODIFIED ⚡**")
                        .setDescription("```diff\n+ CALLSIGN SUCCESSFULLY UPDATED +\n```")
                        .addField("👤 **OPERATOR**", "```\n" + event.getUser().getName() + "\n```", true)
                        .addField("🏷️ **NEW CALLSIGN**", "```\n" + nickname + "\n```", true)
                        .addField("📊 **STATUS**", "```\nACTIVE & UPDATED\n```", true)
                        .setFooter("PMC Personnel Division • Identity Management", event.getJDA().getSelfUser().getAvatarUrl())
                        .setTimestamp(Instant.now());
                    
                    event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                    logCommand(event, "NICKNAME_CHANGE", "New nickname: " + nickname);
                },
                error -> {
                    event.reply("❌ Failed to change nickname. Check permissions.").setEphemeral(true).queue();
                }
            );
        } catch (Exception e) {
            event.reply("❌ Failed to change nickname: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
    
    private void handleAvatar(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(COLOR_INFO)
            .setAuthor("🖼️ PERSONNEL PHOTO RETRIEVAL", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ OPERATOR IDENTIFICATION PHOTO ⚡**")
            .setDescription("```diff\n+ PERSONNEL IMAGE ACCESSED +\n```")
            .addField("👤 **SUBJECT**", "```\n" + targetUser.getName() + "\n```", true)
            .addField("🔍 **IMAGE TYPE**", "```\nPROFILE AVATAR\n```", true)
            .addField("📊 **STATUS**", "```\nRETRIEVED SUCCESSFULLY\n```", true)
            .setImage(targetUser.getAvatarUrl() + "?size=512")
            .setFooter("PMC Intelligence Division • Photo Archive", event.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        logCommand(event, "AVATAR_REQUEST", "Target user: " + targetUser.getName());
    }
    
    private void handleUserInfo(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();
        Member targetMember = event.getGuild().getMember(targetUser);
        
        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }
        
        String roles = targetMember.getRoles().isEmpty() ? "None" : 
            targetMember.getRoles().stream()
                .map(Role::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("None");
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String joinedAt = targetMember.getTimeJoined().format(formatter);
        String createdAt = targetUser.getTimeCreated().format(formatter);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(COLOR_INFO)
            .setAuthor("📋 PERSONNEL DOSSIER", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ OPERATOR INTELLIGENCE FILE ⚡**")
            .setDescription("```diff\n+ PERSONNEL RECORD ACCESSED +\n```")
            .addField("👤 **OPERATOR ID**", "```\n" + targetUser.getName() + "#" + targetUser.getDiscriminator() + "\n```", true)
            .addField("🏷️ **CALLSIGN**", "```\n" + (targetMember.getNickname() != null ? targetMember.getNickname() : "None") + "\n```", true)
            .addField("🎖️ **CLEARANCE LEVEL**", "```\n" + targetMember.getRoles().size() + " active roles\n```", true)
            .addField("📅 **ACCOUNT CREATED**", "```\n" + createdAt + "\n```", true)
            .addField("📅 **JOINED SERVER**", "```\n" + joinedAt + "\n```", true)
            .addField("📊 **STATUS**", "```\n" + targetMember.getOnlineStatus().getKey().toUpperCase() + "\n```", true)
            .addField("🎖️ **ROLE ASSIGNMENTS**", "```\n" + roles + "\n```", false)
            .setThumbnail(targetUser.getAvatarUrl())
            .setFooter("PMC Intelligence Division • Personnel Database", event.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        logCommand(event, "USER_INFO", "Target: " + targetUser.getName());
    }
    
    private void handleHelp(SlashCommandInteractionEvent event) {
        boolean isAdmin = hasRole(event.getMember(), ADMIN_COMMAND_ROLE_ID);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(COLOR_CLASSIFIED)
            .setAuthor("📚 COMMAND REFERENCE MANUAL", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ PMC BOT COMMAND DIRECTORY ⚡**")
            .setDescription("```diff\n+ AUTHORIZED COMMAND REFERENCE +\n```")
            .addField("🎖️ **GENERAL COMMANDS**", 
                "```\n" +
                "/nickname <name> - Change your callsign\n" +
                "/avatar [user] - Get user's profile photo\n" +
                "/userinfo [user] - View personnel dossier\n" +
                "/deploy <required> <startup_info> [message] - Create deployment\n" +
                "/help - Show this command reference\n" +
                "```", false);
        
        if (isAdmin) {
            embed.addField("🛡️ **ADMIN COMMANDS**", 
                "```\n" +
                "/promote <user> <role> <reason> - Promote personnel\n" +
                "/infraction <user> <action> <reason> [role] - Issue discipline\n" +
                "/activitycheck <goal> - Start activity assessment\n" +
                "/modpanel <user> <action> [role] [reason] - Moderation tools\n" +
                "```", false)
            .addField("🔧 **MODERATION ACTIONS**", 
                "```\n" +
                "timeout_5m, timeout_1h, timeout_1d - Apply timeouts\n" +
                "remove_timeout - Remove active timeout\n" +
                "kick - Remove user from server\n" +
                "give_role, remove_role - Role management\n" +
                "```", false);
        }
        
        embed.addField("📍 **DEPLOYMENT SYSTEM**", 
            "```\n" +
            "1. Create deployment with required participant count\n" +
            "2. Users react to confirm participation\n" +
            "3. Auto-starts when quota reached\n" +
            "4. All participants receive startup briefing via DM\n" +
            "```", false)
        .addField("📊 **PERMISSION LEVELS**", 
            "```\n" +
            "Basic Role: General commands + deployments\n" +
            "Admin Role: All commands + moderation panel\n" +
            "```", false)
        .setFooter("PMC Command Center • Bot Documentation v2.1", event.getJDA().getSelfUser().getAvatarUrl())
        .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        logCommand(event, "HELP_REQUEST", "Access level: " + (isAdmin ? "Admin" : "Basic"));
    }

    private boolean hasRole(Member member, long roleId) {
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId);
    }

    private void logCommand(SlashCommandInteractionEvent event, String status, String details) {
        String commandCode = generateCommandCode();
        
        // Format timestamp properly - readable format
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedTime = now.format(formatter);
        
        EmbedBuilder logEmbed = new EmbedBuilder()
            .setColor(COLOR_CLASSIFIED)
            .setAuthor("🔐 CLASSIFIED COMMAND LOG", null, "https://cdn.discordapp.com/emojis/785969405839597628.png")
            .setTitle("**⚡ SYSTEM ACTIVITY RECORDED ⚡**")
            .setDescription("```diff\n+ COMMAND EXECUTION LOGGED +\n```")
            .addField("🆔 **LOG ID**", "```\n" + commandCode + "\n```", true)
            .addField("👤 **OPERATOR**", event.getUser().getAsMention(), true)
            .addField("📍 **LOCATION**", "#" + event.getChannel().getName(), true)
            .addField("⚡ **COMMAND**", "```\n/" + event.getName() + "\n```", true)
            .addField("📊 **STATUS**", "```\n" + status + "\n```", true)
            .addField("🕐 **TIMESTAMP**", "```\n" + formattedTime + "\n```", true)
            .addField("📝 **DETAILS**", "```\n" + details + "\n```", false)
            .setFooter("PMC Security Division • Command Monitoring System", event.getJDA().getSelfUser().getAvatarUrl())
            .setTimestamp(Instant.now());

        TextChannel logChannel = event.getGuild().getTextChannelById(LOG_CHANNEL_ID);
        if (logChannel != null) {
            logChannel.sendMessageEmbeds(logEmbed.build()).queue();
        }

        System.out.println("🔐 Command Log " + commandCode + ": " + event.getUser().getName() + " executed /" + event.getName() + " - " + status);
    }

    private String generateUniqueId() {
        return String.valueOf(new Random().nextInt(900000) + 100000);
    }

    private String generateCommandCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%&*";
        StringBuilder code = new StringBuilder();
        Random random = ThreadLocalRandom.current();
        
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return code.toString();
    }
}

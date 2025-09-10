Military Discord Bot
Overview
A Discord bot written in Java using the JDA (Java Discord API) library. This bot provides military-themed server management commands for Discord servers.

Features
The bot supports the following slash commands:

/promote <user> <role> <reason> - Promote a user to a new rank (Admin only)
/infraction <user> <action> <reason> [demoted_to] - Record staff infractions (Admin only)
/deploy [message] - Create deployment vote with @everyone ping
/activitycheck <goal> - Create activity check with @everyone ping (Admin only)
Security & Permissions
Basic Commands: Requires role ID 1415166608921591861
Admin Commands: Requires role ID 1348877561257791539 (for promote, infraction, activitycheck)
Comprehensive Logging: All commands logged to channel 1415167671044935841 with unique tracking codes
Setup Status
✅ Java Environment: Java 17 with GraalVM installed ✅ Dependencies: JDA library (v5.0.0-beta.20) configured with Maven ✅ Secrets: DISCORD_TOKEN environment variable configured ✅ Workflow: Discord bot running successfully on Replit ✅ Deployment: Configured for VM deployment (always-on)

Project Structure
src/main/java/
  └── MilitaryBot.java     # Main bot class with command handling
pom.xml                    # Maven configuration and dependencies
replit.md                  # This documentation file
Configuration
Bot Token: Stored securely in Replit Secrets as DISCORD_TOKEN
Commands: Slash commands (automatically registered when bot joins server)
Channel IDs: Configured in MilitaryBot.java constants:
Promotion Channel: 1348787021723734076
Infraction Channel: 1348787047300599939
Deployment Channel: 1360030381147160771
Activity Channel: 1361539341754699876
Log Channel: 1415167671044935841
Recent Changes (2025-09-10)
Complete Bot Rewrite: Converted from prefix commands to slash commands
Role-Based Security: Implemented two-tier permission system with role checks
Professional Embeds: Redesigned all embeds with military/classified theme using Discord best practices
Smart Pinging: Fixed deployment/activity commands to properly ping @everyone without requiring manual pings
Enhanced Commands:
Promotions now use role selection and proper authorization format
Infractions support all action types with optional demotion role assignment
Commands show as slash commands with autocomplete options
Comprehensive Logging: Every command execution logged with unique tracking codes to dedicated channel
JDA Compatibility: Updated for JDA v5.0.0 with proper channel and emoji handling
Bot Permissions Required
The bot needs the following Discord permissions:

Send Messages
Use External Emojis
Add Reactions
Manage Roles (for promotion commands)
Read Message History
User Preferences
Language: Java with Maven build system
Framework: JDA (Java Discord API)
Deployment: Always-on VM for continuous Discord connectivity

/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    Copyleft (L) 2016 - 2019 RonanLana (HeavenMS)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package client.processor.stat;

import client.AbstractCharacterObject;
import client.Character;
import client.Client;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.SkillMacro;
import client.autoban.AutobanFactory;
import constants.game.GameConstants;
import constants.skills.Aran;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.PacketCreator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * @author RonanLana - synchronization of SP transaction modules
 */
public class AssignSPProcessor {
    private static final Logger log = LoggerFactory.getLogger(AssignSPProcessor.class);

    public static boolean canSPAssign(Client c, int skillid) {
        if (skillid == Aran.HIDDEN_FULL_DOUBLE || skillid == Aran.HIDDEN_FULL_TRIPLE || skillid == Aran.HIDDEN_OVER_DOUBLE || skillid == Aran.HIDDEN_OVER_TRIPLE) {
            c.sendPacket(PacketCreator.enableActions());
            return false;
        }

        Character player = c.getPlayer();
        if ((!GameConstants.isPqSkillMap(player.getMapId()) && GameConstants.isPqSkill(skillid)) || (!player.isGM() && GameConstants.isGMSkills(skillid)) || (!GameConstants.isInJobTree(skillid, player.getJob().getId()) && !player.isGM())) {
            AutobanFactory.PACKET_EDIT.alert(player, "tried to packet edit in distributing sp.");
            log.warn("Chr {} tried to use skill {} without it being in their job.", c.getPlayer().getName(), skillid);

            c.disconnect(true, false);
            return false;
        }

        return true;
    }

    public static void SPAssignAction(Client c, int skillid) {
        c.lockClient();
        try {
            if (!canSPAssign(c, skillid)) {
                return;
            }

            Character player = c.getPlayer();
            int skillBook = GameConstants.getSkillBook(skillid / 10000);
            int remainingSp = player.getRemainingSps()[skillBook];
            boolean isBeginnerSkill = false;

            if (skillid % 10000000 > 999 && skillid % 10000000 < 1003) {
                int total = 0;
                for (int i = 0; i < 3; i++) {
                    total += player.getSkillLevel(SkillFactory.getSkill(player.getJobType() * 10000000 + 1000 + i));
                }
                remainingSp = Math.min((player.getLevel() - 1), 6) - total;
                isBeginnerSkill = true;
            }
            Skill skill = SkillFactory.getSkill(skillid);
            int curLevel = player.getSkillLevel(skill);
            if ((remainingSp > 0 && curLevel + 1 <= (skill.isFourthJob() ? player.getMasterLevel(skill) : skill.getMaxLevel()))) {
                if (!hasRequiredSkillLevels(skill, player::getSkillLevel)) {
                    player.dropMessage(1, "You haven't met the requirements to learn this skill.");
                    player.sendPacket(PacketCreator.enableActions());
                    return;
                }

                if (!isBeginnerSkill) {
                    if (skillBook != 0) {
                        player.gainSp(-1, skillBook, false);
                    } else if (!player.spendSkillPoint(GameConstants.getJobBranch(skillid / 10000))) {
                        player.dropMessage(1, getReservedSpMessage(player.getLockedSps()));
                        player.sendPacket(PacketCreator.enableActions());
                        return;
                    }
                } else {
                    player.sendPacket(PacketCreator.enableActions());
                }
                if (skill.getId() == Aran.FULL_SWING) {
                    player.changeSkillLevel(skill, (byte) (curLevel + 1), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_FULL_DOUBLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_FULL_TRIPLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                } else if (skill.getId() == Aran.OVER_SWING) {
                    player.changeSkillLevel(skill, (byte) (curLevel + 1), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_OVER_DOUBLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                    player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_OVER_TRIPLE), player.getSkillLevel(skill), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                } else {
                    player.changeSkillLevel(skill, (byte) (curLevel + 1), player.getMasterLevel(skill), player.getSkillExpiration(skill));
                }
            }
        } finally {
            c.unlockClient();
        }
    }

    /**
     * SP Reset scroll: returns all SP spent on the character's skills of the given job tier. A skill required by a
     * learned skill outside the tier keeps the required level. The returned SP can only be spent on that tier.
     *
     * @return true if any SP was returned and the scroll should be consumed
     */
    public static boolean SPFullResetAction(Client c, int jobTier) {
        c.lockClient();
        try {
            Character player = c.getPlayer();
            if (jobTier < 1 || jobTier > AbstractCharacterObject.LOCKED_SP_TIERS) {
                c.sendPacket(PacketCreator.enableActions());
                return false;
            }

            Job job = player.getJob();
            String tierName = GameConstants.ordinal(jobTier);
            if (GameConstants.hasSPTable(job)) {
                return rejectSPReset(c, "Evans can't use SP Reset scrolls.");
            }
            if (GameConstants.getJobBranch(job) < jobTier) {
                return rejectSPReset(c, "You can use this scroll after your " + tierName + " job advancement.");
            }

            Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>(player.getSkills());
            Map<Skill, Integer> resetLevels = getTierResetLevels(skills, skill -> isTierResetSkill(skill.getId(), job.getId(), jobTier));
            if (resetLevels.isEmpty()) {
                return rejectSPReset(c, "You have no " + tierName + " job SP to reset.");
            }

            int refundedSp = 0;
            for (Map.Entry<Skill, Integer> resetLevel : resetLevels.entrySet()) {
                refundedSp += skills.get(resetLevel.getKey()).skillevel - resetLevel.getValue();
            }
            if (refundedSp < 1) {
                return rejectSPReset(c, "Your " + tierName + " job skills are already at the levels required by your higher job skills.");
            }

            Set<Integer> unlearnedSkillIds = new HashSet<>();
            for (Map.Entry<Skill, Integer> resetLevel : resetLevels.entrySet()) {
                Skill skill = resetLevel.getKey();
                Character.SkillEntry entry = skills.get(skill);
                int newLevel = resetLevel.getValue();
                if (newLevel < entry.skillevel) {
                    setResetSkillLevel(player, skill, newLevel, entry.masterlevel, entry.expiration);
                    if (newLevel == 0) {
                        unlearnedSkillIds.add(skill.getId());
                    }
                }
            }

            player.refundLockedSp(refundedSp, jobTier);
            clearSkillMacros(player, unlearnedSkillIds);

            String message = "SP Reset: " + refundedSp + " SP was returned from your " + tierName + " job skills. It can only be spent on " + tierName + " job skills.";
            if (resetLevels.values().stream().anyMatch(level -> level > 0)) {
                message += " Skills required by your higher job skills kept their required level.";
            }
            player.message(message);
            return true;
        } finally {
            c.unlockClient();
        }
    }

    /**
     * Works out the level each learned tier skill is reset to: 0, or the level required by a learned skill that is not
     * reset (including through chains of tier skills that are kept).
     *
     * @param skills      the character's skills
     * @param isTierSkill whether a skill belongs to the tier being reset
     * @return reset level of every learned tier skill, ordered by skill id
     */
    static Map<Skill, Integer> getTierResetLevels(Map<Skill, Character.SkillEntry> skills, Predicate<Skill> isTierSkill) {
        Map<Integer, Skill> tierSkills = new TreeMap<>();
        for (Map.Entry<Skill, Character.SkillEntry> skill : skills.entrySet()) {
            if (skill.getValue().skillevel > 0 && isTierSkill.test(skill.getKey())) {
                tierSkills.put(skill.getKey().getId(), skill.getKey());
            }
        }

        Map<Integer, Integer> requiredLevels = new HashMap<>();
        Deque<Skill> keptSkills = new ArrayDeque<>();
        for (Map.Entry<Skill, Character.SkillEntry> skill : skills.entrySet()) {
            if (skill.getValue().skillevel > 0 && !tierSkills.containsKey(skill.getKey().getId())) {
                addRequiredLevels(skill.getKey(), tierSkills, requiredLevels, keptSkills);
            }
        }
        while (!keptSkills.isEmpty()) {
            addRequiredLevels(keptSkills.pop(), tierSkills, requiredLevels, keptSkills);
        }

        Map<Skill, Integer> resetLevels = new LinkedHashMap<>();
        for (Skill skill : tierSkills.values()) {
            int currentLevel = skills.get(skill).skillevel;
            resetLevels.put(skill, Math.min(currentLevel, requiredLevels.getOrDefault(skill.getId(), 0)));
        }
        return resetLevels;
    }

    private static void addRequiredLevels(Skill learnedSkill, Map<Integer, Skill> tierSkills,
                                          Map<Integer, Integer> requiredLevels, Deque<Skill> keptSkills) {
        for (Map.Entry<Integer, Integer> requirement : learnedSkill.getRequiredSkills().entrySet()) {
            Skill requiredSkill = tierSkills.get(requirement.getKey());
            if (requiredSkill != null && requirement.getValue() > requiredLevels.getOrDefault(requirement.getKey(), 0)) {
                requiredLevels.put(requirement.getKey(), requirement.getValue());
                keptSkills.push(requiredSkill);
            }
        }
    }

    static boolean hasRequiredSkillLevels(Skill skill, IntUnaryOperator skillLevels) {
        for (Map.Entry<Integer, Integer> requirement : skill.getRequiredSkills().entrySet()) {
            if (skillLevels.applyAsInt(requirement.getKey()) < requirement.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTierResetSkill(int skillId, int jobId, int jobTier) {
        return GameConstants.getJobBranch(skillId / 10000) == jobTier
                && GameConstants.isInJobTree(skillId, jobId)
                && !GameConstants.isGMSkills(skillId)
                && !GameConstants.isHiddenSkills(skillId);
    }

    private static void setResetSkillLevel(Character player, Skill skill, int level, int masterLevel, long expiration) {
        player.changeSkillLevel(skill, (byte) level, masterLevel, expiration);
        if (skill.getId() == Aran.FULL_SWING) {
            player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_FULL_DOUBLE), (byte) level, masterLevel, expiration);
            player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_FULL_TRIPLE), (byte) level, masterLevel, expiration);
        } else if (skill.getId() == Aran.OVER_SWING) {
            player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_OVER_DOUBLE), (byte) level, masterLevel, expiration);
            player.changeSkillLevel(SkillFactory.getSkill(Aran.HIDDEN_OVER_TRIPLE), (byte) level, masterLevel, expiration);
        }
    }

    // update macros, thanks to Arnah
    private static void clearSkillMacros(Character player, Set<Integer> skillIds) {
        if (skillIds.isEmpty()) {
            return;
        }

        boolean updated = false;
        for (SkillMacro macro : player.getMacros()) {
            if (macro == null) {
                continue;
            }

            boolean update = false;
            if (skillIds.contains(macro.getSkill1())) {
                update = true;
                macro.setSkill1(0);
            }
            if (skillIds.contains(macro.getSkill2())) {
                update = true;
                macro.setSkill2(0);
            }
            if (skillIds.contains(macro.getSkill3())) {
                update = true;
                macro.setSkill3(0);
            }
            if (update) {
                updated = true;
                player.updateMacros(macro.getPosition(), macro);
            }
        }
        if (updated) {
            player.sendMacros();
        }
    }

    private static boolean rejectSPReset(Client c, String message) {
        c.getPlayer().dropMessage(1, message);
        c.sendPacket(PacketCreator.enableActions());
        return false;
    }

    private static String getReservedSpMessage(int[] lockedSps) {
        List<String> reservations = new ArrayList<>();
        for (int i = 0; i < lockedSps.length; i++) {
            if (lockedSps[i] > 0) {
                reservations.add(lockedSps[i] + " SP for " + GameConstants.ordinal(i + 1) + " job skills");
            }
        }

        if (reservations.isEmpty()) {
            return "You don't have any SP to spend on this skill.";
        }
        return "Your remaining SP is reserved: " + String.join(", ", reservations) + ".";
    }
}

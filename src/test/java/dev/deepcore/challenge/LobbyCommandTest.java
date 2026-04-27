package dev.deepcore.challenge;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.training.TrainingManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class LobbyCommandTest {

    @Test
    void onCommand_rejectsNonPlayers() {
        TrainingManager trainingManager = mock(TrainingManager.class);
        ChallengeSessionManager challengeSessionManager = mock(ChallengeSessionManager.class);
        LobbyCommand commandHandler = new LobbyCommand(trainingManager, challengeSessionManager);

        CommandSender sender = mock(CommandSender.class);
        boolean handled = commandHandler.onCommand(sender, mock(Command.class), "lobby", new String[0]);

        assertTrue(handled);
        verify(sender).sendMessage("Only players can use /lobby.");
        verify(trainingManager, never()).leaveTraining(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onCommand_blocksDuringActiveChallenge() {
        TrainingManager trainingManager = mock(TrainingManager.class);
        ChallengeSessionManager challengeSessionManager = mock(ChallengeSessionManager.class);
        when(challengeSessionManager.isRunningPhase()).thenReturn(true);

        LobbyCommand commandHandler = new LobbyCommand(trainingManager, challengeSessionManager);
        Player player = mock(Player.class);

        boolean handled = commandHandler.onCommand(player, mock(Command.class), "lobby", new String[0]);

        assertTrue(handled);
        verify(player).sendMessage(org.mockito.ArgumentMatchers.contains("You cannot use /lobby"));
        verify(trainingManager, never()).leaveTraining(player);
    }

    @Test
    void onCommand_leavesTrainingWhenIdle() {
        TrainingManager trainingManager = mock(TrainingManager.class);
        ChallengeSessionManager challengeSessionManager = mock(ChallengeSessionManager.class);
        when(challengeSessionManager.isRunningPhase()).thenReturn(false);

        LobbyCommand commandHandler = new LobbyCommand(trainingManager, challengeSessionManager);
        Player player = mock(Player.class);

        boolean handled = commandHandler.onCommand(player, mock(Command.class), "lobby", new String[0]);

        assertTrue(handled);
        verify(trainingManager).leaveTraining(player);
    }
}

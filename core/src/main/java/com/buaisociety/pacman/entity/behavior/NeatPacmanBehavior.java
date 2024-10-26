package com.buaisociety.pacman.entity.behavior;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.buaisociety.pacman.sprite.DebugDrawing;
import com.cjcrafter.neat.Client;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.PacmanEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2f;
import org.joml.Vector2i;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.Random;

public class NeatPacmanBehavior implements Behavior {

    private final @NotNull Client client;
    private @Nullable PacmanEntity pacman;

    // Score modifiers help us maintain "multiple pools" of points.
    // This is great for training, because we can take away points from
    // specific pools of points instead of subtracting from all.
    private int scoreModifier = 0;

    private int numUpdatesSinceLastScore = 0;
    private int lastScore = 0;

    public NeatPacmanBehavior(@NotNull Client client) {
        this.client = client;
    }

    /**
     * Returns the desired direction that the entity should move towards.
     *
     * @param entity the entity to get the direction for
     * @return the desired direction for the entity
     */
    @NotNull
    @Override
    public Direction getDirection(@NotNull Entity entity) {
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
        }

        // SPECIAL TRAINING CONDITIONS
        int newScore = pacman.getMaze().getLevelManager().getScore();
        if (newScore > lastScore) {
            lastScore = newScore;
            numUpdatesSinceLastScore = 0;
        }

        if (numUpdatesSinceLastScore++ > 60 * 10) {
            pacman.kill();

            // Ends the training early
            return Direction.UP;
        }
        // END OF SPECIAL TRAINING CONDITIONS

        // We are going to use these directions a lot for different inputs. Get them all once for clarity and brevity
        Direction forward = pacman.getDirection();
        Direction left = pacman.getDirection().left();
        Direction right = pacman.getDirection().right();
        Direction behind = pacman.getDirection().behind();

        // Input nodes 1, 2, 3, and 4 show if the pacman can move in the forward, left, right, and behind directions
        boolean canMoveForward = pacman.canMove(forward);
        boolean canMoveLeft = pacman.canMove(left);
        boolean canMoveRight = pacman.canMove(right);
        boolean canMoveBehind = pacman.canMove(behind);

    // Getting closest pellet using Searcher class
        Tile currentTile = pacman.getMaze().getTile(pacman.getTilePosition());
        Map<Direction, Searcher.SearchResult> nearestPellets = Searcher.findTileInAllDirections(currentTile, tile -> tile.getState() == TileState.PELLET);

        int maxDistance = -1;
        for (Searcher.SearchResult result : nearestPellets.values()) {
            if (result != null) {
                maxDistance = Math.max(maxDistance, result.getDistance());
            }
        }

        float nearestPelletForward = nearestPellets.get(forward) != null ? 1 - (float) nearestPellets.get(forward).getDistance() / maxDistance : 0;
        float nearestPelletLeft = nearestPellets.get(left) != null ? 1 - (float) nearestPellets.get(left).getDistance() / maxDistance : 0;
        float nearestPelletRight = nearestPellets.get(right) != null ? 1 - (float) nearestPellets.get(right).getDistance() / maxDistance : 0;
        float nearestPelletBehind = nearestPellets.get(behind) != null ? 1 - (float) nearestPellets.get(behind).getDistance() / maxDistance : 0;

        float[] outputs = client.getCalculator().calculate(new float[]{
            canMoveForward ? 1f : 0f,
            canMoveLeft ? 1f : 0f,
            canMoveRight ? 1f : 0f,
            canMoveBehind ? 1f : 0f,
            nearestPelletForward,
            nearestPelletLeft,
            nearestPelletRight,
            nearestPelletBehind
        }).join();

        // Figuring out which direction to go in
        int index = 0;
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) {
                max = outputs[i];
                index = i;
            }
        }

        Direction newDirection = switch (index) {
            case 0 -> pacman.getDirection();
            case 1 -> pacman.getDirection().left();
            case 2 -> pacman.getDirection().right();
            case 3 -> pacman.getDirection().behind();
            default -> throw new IllegalStateException("Unexpected value: " + index);
        };


        // This is the fitness function
        // If modifing, use the score modifier
        client.setScore(pacman.getMaze().getLevelManager().getScore() + scoreModifier);
        return newDirection;
    }


    @Override
    public void render(@NotNull SpriteBatch batch) {
        // TODO: You can render debug information here
        
        // if (pacman != null) {
        //     DebugDrawing.outlineTile(batch, pacman.getMaze().getTile(pacman.getTilePosition()), Color.RED);
        //     DebugDrawing.drawDirection(batch, pacman.getTilePosition().x() * Maze.TILE_SIZE, pacman.getTilePosition().y() * Maze.TILE_SIZE, pacman.getDirection(), Color.RED);
        // }
         
    }

    // public Direction vecToPacRelDirection(Vector2i vector) {
    //     if (vector.x == 0){
    //         if (vector.y == 1){
    //             return pacman.getDirection().left();
    //         }
    //         else{
    //             return pacman.getDirection().right(); 
    //         }
    //     }
    //     else{
    //         if (vector.x == 0){
    //             return pacman.getDirection(); 
    //         }
    //         else{ 
    //             return pacman.getDirection().behind(); 
    //         }
    //     }
    // }


    // private Vector2i relativePosToAbsDirection(Vector2i relPos) {
    //     // Takes a relative position (dir to pellet) and pacman's direction
    //     // Returns a unit vector that represents pacman's next move

    //     // Convert Vector2i to Vector2f for normalization
    //     Vector2f relPosFloat = new Vector2f(relPos.x, relPos.y);

    //     // Normalize the vector
    //     relPosFloat.normalize();

    //     // Convert back to Vector2i with length 1
    //     Vector2i direction = new Vector2i(Math.round(relPosFloat.x), Math.round(relPosFloat.y));

    //     // Now we have a direction vector with 1 as its components
    //     // We now need to take into account pacman's direction

    //     // (-1, 0) & right => (-1, 0) 
    //     // (-1, 0) & left => (1, 0)
    //     // (0, 1) & up => (1, 0)
    //     // (1, 1) & down => (0, 1)

    //         // Adjust the direction based on Pacman's current direction
    // Direction pacmanDirection = pacman.getDirection();

    // switch (pacmanDirection) {
    //     case UP:
    //         // No change needed, direction is already relative to UP
    //         break;
    //     case DOWN:
    //         // Invert the y-axis
    //         direction.set(direction.x, -direction.y);
    //         break;
    //     case LEFT:
    //         // Swap x and y, and invert the x-axis
    //         direction.set(-direction.y, direction.x);
    //         break;
    //     case RIGHT:
    //         // Swap x and y, and invert the y-axis
    //         direction.set(direction.y, -direction.x);
    //         break;
    //     }

    // return direction;
    // }



    // final Vector2i up = new Vector2i(0,1);
    // final Vector2i down = new Vector2i(0,-1);
    // final Vector2i left = new Vector2i(1,0);
    // final Vector2i right = new Vector2i(-1,0);
    
    // private Vector2i getClosestPellet() {

    //     int magnitude = 1;
    //     while (true) {
    //         try {
    //             if (getTileState(up.mul(magnitude)) == TileState.PELLET) 
    //                 return up.mul(magnitude);
    //         } finally {}

    //         try {
    //         if (getTileState(down.mul(magnitude)) == TileState.PELLET) 
    //             return down.mul(magnitude);
    //         } finally {}

    //         try {
    //         if (getTileState(left.mul(magnitude)) == TileState.PELLET) 
    //             return left.mul(magnitude);
    //         } finally {}

    //         try {
    //         if (getTileState(right.mul(magnitude)) == TileState.PELLET) 
    //             return right.mul(magnitude);
    //         } finally {}

    //         magnitude++;
    //     }
    // }

    // private TileState getTileState(Vector2i offset) {
    //     // Gets the tile state relative to the pacman entity
    //     return pacman.getMaze().getTile(pacman.getTilePosition().add(offset)).getState();
    // }

    // private boolean hasPellet(TileState titleState) {
    //     return titleState == TileState.PELLET;
    // }
}

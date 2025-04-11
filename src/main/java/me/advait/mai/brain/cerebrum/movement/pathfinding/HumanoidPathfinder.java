package me.advait.mai.brain.cerebrum.movement.pathfinding;

import de.metaphoriker.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.metaphoriker.pathetic.api.pathing.filter.PathFilter;
import de.metaphoriker.pathetic.api.pathing.filter.PathFilterStage;
import de.metaphoriker.pathetic.api.provider.NavigationPointProvider;
import de.metaphoriker.pathetic.api.wrapper.Depth;
import de.metaphoriker.pathetic.api.wrapper.PathPosition;
import de.metaphoriker.pathetic.api.wrapper.PathVector;
import de.metaphoriker.pathetic.engine.Node;
import de.metaphoriker.pathetic.engine.pathfinder.AStarPathfinder;
import de.metaphoriker.pathetic.shaded.jheaps.tree.FibonacciHeap;

import java.util.*;

public class HumanoidPathfinder extends AStarPathfinder {

    private final PathVector[] movementVectors = Arrays.stream(MovementType.values())
            .flatMap(type -> Arrays.stream(type.getVectors()))
            .toArray(PathVector[]::new);

    public HumanoidPathfinder(NavigationPointProvider navigationPointProvider, PathfinderConfiguration pathfinderConfiguration) {
        super(navigationPointProvider, pathfinderConfiguration);
    }

    @Override
    protected void tick(PathPosition start, PathPosition target, Node currentNode, Depth depth, FibonacciHeap<Double, Node> nodeQueue, List<PathFilter> filters, List<PathFilterStage> filterStages) {
        this.evaluateNewNodes(nodeQueue, currentNode, filters, filterStages);
        depth.increment();
    }

    /** OVERRIDING PATHETIC */
    protected void evaluateNewNodes(FibonacciHeap<Double, Node> nodeQueue, Node currentNode, List<PathFilter> filters, List<PathFilterStage> filterStages) {
        for (Node newNode : this.fetchValidNeighbours(currentNode, filters, filterStages)) {
            double nodeCost = newNode.getHeuristic().get();
            nodeQueue.insert(nodeCost, newNode);
        }

    }

    /** OVERRIDING PATHETIC */
    protected Collection<Node> fetchValidNeighbours(Node currentNode, List<PathFilter> filters, List<PathFilterStage> filterStages) {
        Set<Node> newNodes = new HashSet<>();

        for (PathVector vector : movementVectors) {
            PathPosition newPos = currentNode.getPosition().add(vector);
            Node newNode = new Node(
                    newPos,
                    currentNode.getStart(),
                    currentNode.getTarget(),
                    this.pathfinderConfiguration.getHeuristicWeights(),
                    currentNode.getDepth() + 1
            );
            newNode.setParent(currentNode);

            if (doAllFiltersPass(filters, newNode) && doAnyFilterStagePass(filterStages, newNode)) {
                newNodes.add(newNode);
            }
        }

        return newNodes;
    }

    private boolean doAllFiltersPass(List<PathFilter> filters, Node node) {
        for (PathFilter filter : filters) {
            var context = new de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext(
                    node.getPosition(),
                    node.getParent() != null ? node.getParent().getPosition() : null,
                    node.getStart(),
                    node.getTarget(),
                    super.pathfinderConfiguration.getProvider()
            );
            if (!filter.filter(context)) return false;
        }
        return true;
    }

    private boolean doAnyFilterStagePass(List<PathFilterStage> filterStages, Node node) {
        if (filterStages.isEmpty()) return true;
        for (PathFilterStage stage : filterStages) {
            var context = new de.metaphoriker.pathetic.api.pathing.filter.PathValidationContext(
                    node.getPosition(),
                    node.getParent() != null ? node.getParent().getPosition() : null,
                    node.getStart(),
                    node.getTarget(),
                    super.pathfinderConfiguration.getProvider()
            );
            if (stage.filter(context)) return true;
        }
        return false;
    }

    public enum MovementType {
        WALK(new PathVector[]{
                new PathVector(1, 0, 0), new PathVector(-1, 0, 0),
                new PathVector(0, 0, 1), new PathVector(0, 0, -1)
        }),

        JUMP(new PathVector[]{
                new PathVector(1, 1, 0), new PathVector(-1, 1, 0),
                new PathVector(0, 1, 1), new PathVector(0, 1, -1)
        }),

        PARKOUR(new PathVector[]{
                new PathVector(2, 0, 0), new PathVector(-2, 0, 0),
                new PathVector(3, 0, 0), new PathVector(-3, 0, 0),
                new PathVector(0, 0, 2), new PathVector(0, 0, -2),
                new PathVector(0, 0, 3), new PathVector(0, 0, -3)
        }),

        FALL(generateFallVectors());

        private final PathVector[] vectors;

        MovementType(PathVector[] vectors) {
            this.vectors = vectors;
        }

        public PathVector[] getVectors() {
            return vectors;
        }

        private static PathVector[] generateFallVectors() {
            List<PathVector> fallVectors = new ArrayList<>();
            for (int dy = 1; dy <= 3; dy++) {
                fallVectors.addAll(List.of(
                        new PathVector(0, -dy, 0),
                        new PathVector(1, -dy, 0),
                        new PathVector(-1, -dy, 0),
                        new PathVector(0, -dy, 1),
                        new PathVector(0, -dy, -1)
                ));
            }
            return fallVectors.toArray(PathVector[]::new);
        }
    }
}

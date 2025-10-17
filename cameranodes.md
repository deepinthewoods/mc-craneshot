This is a system I would like to add where we will have nodes, points in 3d space, which control camera behaviour based on the player's distance to them. Our mod doesn't have a traditional "freecam" mode, it has post-movement mouse and keys behavior settings which in combination add up to a traditional freecam. I will use freecam to describe some situations where the camera is controlled by the player directly rather than following the player position. 
(read lines.md for line drawing technique)
Node editing mode
We will add an option to the post-movement mouse slot called "node edit". This will only be available if the post movement keys setting is "move camera". When this mouse setting is active we will be in "node editing mode"
In this mode the mouse cursor will be visible like in the inventory/menus. The player can rotate the camera by dragging on the screen. There will be buttons on the top right corner of the screen. 

Buttons:
Color - opens a modal with h s and v sliders and a colour preview.Add node -will use current camera position.
(When a node is selected)Delete node


A list of associated areas will be here, clicking on one will bring up the area settings modal for it. Each area will have a - button to its right which will delete it.Add area(+ button, uses current camera position as center, default cube with Radius 8)Set LookAtUnset LookAt


(Node types: CameraControl,
DroneShot)

Each node type will have it's own CameraMovement class, and we will have a settings list here the same as we have for our different camera slots. Each node will have a CameraMovement object associated with it. These will be calculated separately from the player's active camera movement. 
In node editing mode when the player left-clicks the screen, it will select the nearest(in screen space) node. 
Each node will have a list of areas associated with it. The area settings button will open a modal window with the following: Check boxes for filtering by player movement, all checked by default(walking, elytra, Minecart, riding ghast, riding other, boat, swimming, crawling)
Type(button, cycles between sphere and cube)Min and max Radius(sliders)An expanded settings button with "..." As it's text. When enabled, we will instead show x, y, and z radii sliders, and also x y and z position labels. Left clicking on a position label will add 1 to its value, right clicking will subtract one. Ctrl clicking will modify by 10, shift clicking by 100.

Areas will render as lines showing either the full cuboid for cube mode, or just ovals for sphere mode. The inner area will be solid lines, the outer area will be dashed lines. The ovals can we quote lo fi they don't need high accuracy. Area lines will use the colour of the node they're associated with. If an area is associated with more than one node it will cycle through the colours, changing every 500ms. 
Actual nodes will render as a solid square with the node's colour, with animated dashed lines connecting it to the center of each of it's areas. If selected the square will have a white outline.
We'll use the areas to calculate an interpolation factor for the camera based on player position. Area maximum is where it will start to influence the camera, area minimum is where the node will have 100% influence on the camera. Overlapping areas will add together and normalize if total is more than 100%. So as the player moves towards the min area the camera will move towards the node. 
Each node will also optionally have a LookAt point associated with it. We will interpolate the camera rotation to in a similar way to the position but we'll calculated the camera position first, then calculate direction of LookAt point from there and interpolate towards that angle. LookAt points will render as 2 small concentric circles, connected to the node with chevrons >>. .

I would like this to be able to work if the server doesn't have it installed, so it could save the nodes client-side, but also if the server has it installed it should save nodes server-side and send them to all players.



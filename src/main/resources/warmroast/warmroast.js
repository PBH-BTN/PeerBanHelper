$(".name").on("click", function (event) {
    var $parent = $(this).parent();
    if ($parent.hasClass("collapsed")) {
        $parent.removeClass("collapsed");
        $parent.children("ul").slideDown(50);
    } else {
        $parent.addClass("collapsed");
        $parent.children("ul").slideUp(50);
    }
});

function extractTime($el) {
    var text = $el.children(".name")
        .children(".time").text().replace(/[^0-9]/, "");
    return parseInt(text);
}

var $overlay = $("#overlay");

$(".name").on("mouseenter", function (event) {
    var $this = $(this);
    var thisTime = null;
    $overlay.empty();
    $this.parents(".node").each(function (i, parent) {
        var $parent = $(parent);
        var time = extractTime($parent);
        if (thisTime == null) {
            thisTime = time;
        } else {
            var $el = $(document.createElement("span"));
            var pos = $parent.position();
            var width = $el.outerWidth();
            $el.text(((thisTime / time) * 100).toFixed(2) + "%");
            $el.css({
                top: pos.top + "px"
            });
            $overlay.append($el);
        }
    });
});

$(".loading").hide();
$(".stack").show();
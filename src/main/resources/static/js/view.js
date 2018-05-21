var lastResult;

$(document).ready(function() {
    
//    $('#keywords').jQCloud([], {
//    	  width: 500,
//    	  height: 350
//	});
    
    /**
     * Get Tag data
     */
    var getWordData = function() {
    	$.ajax({
    		url: 'api/words'+(group ? '?group='+group : ''),
    		type: 'GET',
    		success: function(data) {
    			viewTimer = null;
    			var words = [];
//    			data.forEach(function(d) {words.push({text: d.name, weight: d.value})});
//    			for (var key in data) {
//    				words.push({text: key, weight: data[key]});
//    			}
//    			$('#keywords').jQCloud('update', words);
    			createCloud(data);
//    			console.log('data', data);
        	}
    	});
    }
    getWordData();
    setInterval(getWordData, 30*1000);

    function createCloud(data) {
    	var chart = echarts.init(document.getElementById('keywords'));
    	chart.setOption({
    	    series: [{
    	        type: 'wordCloud',
    	        shape: 'circle',
    	        left: 'center',
    	        top: 'center',
    	        width: '70%',
    	        height: '100%',
    	        right: null,
    	        bottom: null,
    	        sizeRange: [24, 100],
    	        rotationRange: [-90, 90],
    	        rotationStep: 45,
    	        gridSize: 10,
    	        drawOutOfBound: false,
    	        textStyle: {
    	            normal: {
    	                fontFamily: 'sans-serif',
    	                fontWeight: 'bold',
    	                // Color can be a callback function or a color string
    	                color: function () {
    	                    // Random color
    	                    return 'rgb(' + [
    	                        Math.round(Math.random() * 160),
    	                        Math.round(Math.random() * 160),
    	                        Math.round(Math.random() * 160)
    	                    ].join(',') + ')';
    	                }
    	            },
    	            emphasis: {
    	                shadowBlur: 10,
    	                shadowColor: '#333'
    	            }
    	        },
    	        data: data
    	    }]
    	});
    	
    }
    
});
